package com.refillradar.auth;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.refillradar.domain.Account;
import com.refillradar.domain.AccountRole;
import com.refillradar.store.AccountRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Registration, login and logout.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /**
     * The longest password BCrypt actually reads.
     *
     * <p>BCrypt silently ignores everything past 72 <em>bytes</em>. Left unchecked, two
     * different long passphrases that share a 72-byte prefix would both open the same
     * account, and the user would have no way to know. Rejecting the input is the only
     * honest option: truncating it quietly would mean storing something the person did not
     * choose. Bytes, not characters - an emoji or an accented letter is several bytes.
     */
    public static final int MAX_PASSWORD_BYTES = 72;

    /** The shortest password accepted. */
    public static final int MIN_PASSWORD_LENGTH = 12;

    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final LoginThrottle throttle;
    private final Clock clock;

    private final SecurityContextRepository contextRepository =
            new HttpSessionSecurityContextRepository();

    /**
     * @param accounts              where accounts are stored
     * @param passwordEncoder       hashes and verifies passwords
     * @param authenticationManager performs the credential check on login
     * @param throttle              limits how often credentials may be guessed
     * @param clock                 supplies registration timestamps
     */
    public AuthController(AccountRepository accounts,
                          PasswordEncoder passwordEncoder,
                          AuthenticationManager authenticationManager,
                          LoginThrottle throttle,
                          Clock clock) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.throttle = throttle;
        this.clock = clock;
    }

    /**
     * Registers a new account.
     *
     * <p>Every account is created as {@link AccountRole#USER}. There is deliberately no way
     * to ask for {@code ADMIN} over HTTP: a role field a client could set is a privilege
     * escalation with extra steps, however carefully the handler validates it today.
     *
     * @param request     the requested username and password
     * @param httpRequest the servlet request, for the caller's address
     * @return {@code 201 Created} with the account, {@code 409} if the username is taken, or
     *         {@code 429} if this address has registered too often
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegistrationRequest request,
                                      HttpServletRequest httpRequest) {
        String clientIp = clientIp(httpRequest);
        LoginThrottle.Decision decision = throttle.checkRegistration(clientIp);
        if (!decision.allowed()) {
            return tooManyAttempts(decision,
                    "Too many registration attempts from this address. Try again later.");
        }

        byte[] passwordBytes = request.password().getBytes(StandardCharsets.UTF_8);
        if (passwordBytes.length > MAX_PASSWORD_BYTES) {
            return ResponseEntity.badRequest().body(new AuthError("password_too_long",
                    "Password must be at most " + MAX_PASSWORD_BYTES + " bytes. Longer ones "
                            + "would be silently truncated by the hashing algorithm, so we "
                            + "will not accept what we cannot store faithfully."));
        }

        // Counted before the answer is known, because a conflict is as much an attempt as a
        // creation - and it is the conflict that tells a caller whether a username is taken.
        throttle.recordRegistration(clientIp);

        if (accounts.usernameExists(request.username())) {
            // This does leak that the username is taken - unavoidable, since registration
            // has to say whether it worked. Login stays deliberately vague instead.
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new AuthError("username_taken", "That username is already in use."));
        }

        Account account = new Account(
                UUID.randomUUID().toString(),
                request.username(),
                passwordEncoder.encode(request.password()),
                AccountRole.USER,
                Instant.now(clock));

        accounts.save(account);

        return ResponseEntity.status(HttpStatus.CREATED).body(AccountResponse.of(account));
    }

    /**
     * Logs in and establishes a session.
     *
     * @param request  the credentials
     * @param httpRequest  the servlet request, for the session
     * @param httpResponse the servlet response, for the session cookie
     * @return the account, {@code 401} if the credentials are wrong, or {@code 429} if
     *         they have been guessed at too often
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request,
                                   HttpServletRequest httpRequest,
                                   HttpServletResponse httpResponse) {
        String clientIp = clientIp(httpRequest);
        LoginThrottle.Decision decision = throttle.checkLogin(request.username(), clientIp);
        if (!decision.allowed()) {
            // Refused before the password is verified, not after. Verifying a BCrypt hash is
            // deliberately slow, so answering an attacker's thousandth guess costs this
            // server far more than sending it costs them. The cheap refusal is the point.
            return tooManyAttempts(decision,
                    "Too many failed attempts. Try again later.");
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.username(), request.password()));
        } catch (AuthenticationException failed) {
            throttle.recordLoginFailure(request.username(), clientIp);
            // One message for "no such user" and for "wrong password". Distinguishing them
            // turns the login form into a tool for discovering who has an account here,
            // which for this application is who is managing a medication.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AuthError("bad_credentials", "Username or password is incorrect."));
        }

        // A correct password clears this username's failures, so someone who mistyped four
        // times is not left locked out by their own successful fifth attempt.
        throttle.recordLoginSuccess(request.username());

        // Spring Security 6 no longer saves the context implicitly - an authentication that
        // is never stored produces a login that appears to work and a session that is empty
        // on the next request.
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, httpRequest, httpResponse);

        AccountPrincipal principal = (AccountPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(new AccountResponse(
                principal.accountId(), principal.getUsername(),
                principal.getAuthorities().iterator().next().getAuthority()));
    }

    /**
     * Who the caller currently is.
     *
     * @param principal the logged-in account, injected from the session
     * @return the account
     */
    @GetMapping("/me")
    public AccountResponse me(@AuthenticationPrincipal AccountPrincipal principal) {
        return new AccountResponse(principal.accountId(), principal.getUsername(),
                principal.getAuthorities().iterator().next().getAuthority());
    }

    /**
     * Refuses an attempt and says when to come back.
     *
     * @param decision the refusal, carrying the wait
     * @param message  what to tell the caller
     * @return a {@code 429} with a {@code Retry-After} header
     */
    private ResponseEntity<?> tooManyAttempts(LoginThrottle.Decision decision, String message) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(decision.retryAfterSeconds()))
                .body(new AuthError("too_many_attempts", message));
    }

    /**
     * The address a request came from.
     *
     * <p>The socket's address, deliberately not {@code X-Forwarded-For}. That header is a
     * string the client chooses, so trusting it hands an attacker both halves of the
     * problem: a fresh budget on every request, and the ability to spend somebody else's.
     * Behind a real proxy the fix is {@code server.forward-headers-strategy}, which resolves
     * the header where the trusted-proxy list lives; see the README.
     *
     * @param request the servlet request
     * @return the caller's address, or {@code unknown} if the container has none
     */
    private String clientIp(HttpServletRequest request) {
        String address = request.getRemoteAddr();
        return address == null || address.isBlank() ? "unknown" : address;
    }

    /**
     * What a client sends to register.
     *
     * <p>No composition rules - no "must contain a symbol". NIST SP 800-63B recommends
     * against them, and the reason is behavioural rather than mathematical: they reliably
     * produce {@code Password1!}, which is shorter and more guessable than a passphrase the
     * person would actually remember. Length is the requirement that helps.
     *
     * @param username what the person types to log in
     * @param password the plaintext password, which is hashed immediately and never stored
     */
    public record RegistrationRequest(
            @NotBlank(message = "username is required")
            @Size(min = 3, max = 60, message = "username must be 3-60 characters")
            String username,

            @NotBlank(message = "password is required")
            @Size(min = MIN_PASSWORD_LENGTH,
                  message = "password must be at least " + MIN_PASSWORD_LENGTH + " characters "
                          + "- length beats symbols, so a memorable phrase is ideal")
            String password) {
    }

    /**
     * What a client sends to log in.
     *
     * @param username the username
     * @param password the plaintext password
     */
    public record LoginRequest(
            @NotBlank(message = "username is required") String username,
            @NotBlank(message = "password is required") String password) {
    }

    /**
     * What the API returns about an account.
     *
     * <p>A separate type from {@link Account} so there is no field that <em>could</em> carry
     * the hash. Relying on an annotation to hide it means one refactor away from a leak.
     *
     * @param id       the account id
     * @param username the username
     * @param role     the granted authority, e.g. {@code ROLE_USER}
     */
    public record AccountResponse(String id, String username, String role) {

        /**
         * Builds a response from an account, dropping everything secret.
         *
         * @param account the account
         * @return the safe view
         */
        public static AccountResponse of(Account account) {
            return new AccountResponse(account.id(), account.username(),
                    "ROLE_" + account.role().name());
        }
    }

    /**
     * An auth failure a client can act on.
     *
     * @param error   a stable machine-readable code
     * @param message a human-readable explanation
     */
    public record AuthError(String error, String message) {
    }
}
