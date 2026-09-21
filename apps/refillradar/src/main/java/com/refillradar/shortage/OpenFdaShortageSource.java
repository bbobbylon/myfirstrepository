package com.refillradar.shortage;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import com.refillradar.domain.ShortageRecord;

/**
 * Fetches live shortage data from the openFDA {@code /drug/shortages.json} endpoint.
 *
 * <p>Active when {@code refillradar.shortage-source=openfda}. The default is the fixture
 * source, so a misconfigured deployment fails towards <em>obviously fake</em> data rather
 * than towards silently broken live data.
 *
 * <p>Two openFDA behaviours worth knowing before debugging this: {@code limit} <b>defaults
 * to 1</b> (omit it and the API returns one record while looking successful), and
 * <b>caps at 100</b> - so with 200+ active shortages {@link #fetchCurrentShortages()} pages
 * with {@code skip} until a short page arrives.
 *
 * <p>⚠️ <b>Not verified against the live API.</b> This environment blocks {@code api.fda.gov}
 * at the egress proxy, so the class is built from openFDA's published documentation and
 * exercised against recorded fixtures. Treat the first live run as part of the work: confirm
 * field names, date formats and the {@code status} vocabulary.
 */
@Component
@ConditionalOnProperty(name = "refillradar.shortage-source", havingValue = "openfda")
public class OpenFdaShortageSource implements ShortageSource {

    private static final Logger log = LoggerFactory.getLogger(OpenFdaShortageSource.class);

    /** openFDA's hard maximum for a single request. */
    private static final int PAGE_SIZE = 100;

    /** Safety valve: stop after this many pages so a pagination bug cannot loop forever. */
    private static final int MAX_PAGES = 20;

    private final RestClient restClient;
    private final String endpoint;

    /**
     * @param restClientBuilder Spring's preconfigured builder (honours proxy and timeouts)
     * @param endpoint          the shortages endpoint, overridable for testing against a stub
     */
    public OpenFdaShortageSource(RestClient.Builder restClientBuilder,
                                 @Value("${refillradar.openfda.endpoint:https://api.fda.gov/drug/shortages.json}")
                                 String endpoint) {
        this.restClient = restClientBuilder.build();
        this.endpoint = endpoint;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Pages through the feed accumulating every record. A failure partway through aborts
     * the whole fetch with {@link ShortageFetchException} rather than returning a partial
     * list, because a truncated feed would silently produce false negatives - the caller
     * would conclude a drug is fine when its record simply never arrived.
     */
    @Override
    public List<ShortageRecord> fetchCurrentShortages() {
        List<ShortageRecord> all = new ArrayList<>();

        for (int page = 0; page < MAX_PAGES; page++) {
            int skip = page * PAGE_SIZE;
            String url = UriComponentsBuilder.fromUriString(endpoint)
                    .queryParam("limit", PAGE_SIZE)
                    .queryParam("skip", skip)
                    .toUriString();

            OpenFdaResponse response;
            try {
                response = restClient.get().uri(url).retrieve().body(OpenFdaResponse.class);
            } catch (RestClientException e) {
                throw new ShortageFetchException(
                        "openFDA request failed at skip=" + skip + " (" + url + ")", e);
            }

            if (response == null || response.results == null || response.results.isEmpty()) {
                break;
            }

            all.addAll(FixtureShortageSource.toDomain(response));

            // A page shorter than the maximum means we have reached the end of the feed.
            if (response.results.size() < PAGE_SIZE) {
                break;
            }
        }

        log.info("Fetched {} shortage records from openFDA", all.size());
        return all;
    }

    /** {@inheritDoc} */
    @Override
    public String describeSource() {
        return "US FDA drug shortage database (openFDA)";
    }
}
