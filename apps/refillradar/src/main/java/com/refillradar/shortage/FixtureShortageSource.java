package com.refillradar.shortage;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.refillradar.domain.ShortageRecord;
import com.refillradar.domain.ShortageStatus;

/**
 * Replays a recorded openFDA response from the classpath instead of calling the live API.
 *
 * <p>Active when {@code refillradar.shortage-source=fixture}, which is the default in tests
 * and in local development. See {@link ShortageSource} for why this seam exists.
 *
 * <p>The fixture deliberately contains awkward records as well as clean ones - an
 * unrecognised status, a missing brand name, a resolved shortage, a combination product -
 * because those are precisely the cases a live feed will not produce on demand and where
 * the matching logic is most likely to be wrong.
 */
@Component
@ConditionalOnProperty(name = "refillradar.shortage-source", havingValue = "fixture",
        matchIfMissing = true)
public class FixtureShortageSource implements ShortageSource {

    private static final Logger log = LoggerFactory.getLogger(FixtureShortageSource.class);

    /** Classpath location of the recorded response. */
    private static final String FIXTURE_PATH = "fixtures/openfda-shortages-sample.json";

    private final ObjectMapper objectMapper;

    /**
     * @param objectMapper Spring Boot's preconfigured Jackson mapper
     */
    public FixtureShortageSource(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Reads and parses the bundled fixture on every call. No caching: the file is a few
     * kilobytes, and re-reading keeps behaviour identical between calls, which makes test
     * ordering irrelevant.
     */
    @Override
    public List<ShortageRecord> fetchCurrentShortages() {
        try (InputStream in = new ClassPathResource(FIXTURE_PATH).getInputStream()) {
            OpenFdaResponse response = objectMapper.readValue(in, OpenFdaResponse.class);
            List<ShortageRecord> records = toDomain(response);
            log.info("Loaded {} shortage records from fixture {}", records.size(), FIXTURE_PATH);
            return records;
        } catch (IOException e) {
            throw new ShortageFetchException("Could not read shortage fixture " + FIXTURE_PATH, e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public String describeSource() {
        // Explicitly says "not live" so a misconfigured deployment is obvious in the UI
        // rather than quietly serving stale sample data as though it were real.
        return "Recorded FDA sample data (NOT LIVE - development fixture)";
    }

    /**
     * Converts the parsed FDA payload into domain records.
     *
     * <p>Shared shape with {@link OpenFdaShortageSource#toDomain}; kept as a small
     * duplicated method rather than a shared base class because the two sources are
     * otherwise unrelated and inheritance here would couple them for no benefit.
     *
     * @param response the parsed payload; may be {@code null} or contain no results
     * @return domain records, never {@code null}
     */
    static List<ShortageRecord> toDomain(OpenFdaResponse response) {
        if (response == null || response.results == null) {
            return List.of();
        }
        List<ShortageRecord> records = new ArrayList<>(response.results.size());
        for (OpenFdaResponse.Result r : response.results) {
            records.add(new ShortageRecord(
                    r.genericName,
                    r.proprietaryName,
                    r.companyName,
                    ShortageStatus.fromFdaStatus(r.status),
                    r.availability,
                    r.shortageReason,
                    r.therapeuticCategory,
                    r.dosageForm,
                    r.strength,
                    FdaDateParser.parseOrNull(r.initialPostingDate),
                    FdaDateParser.parseOrNull(r.updateDate)));
        }
        return records;
    }
}
