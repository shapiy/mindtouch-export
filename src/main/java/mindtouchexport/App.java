package mindtouchexport;

import okhttp3.*;
import org.apache.commons.codec.binary.Hex;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.awaitility.Awaitility.await;

@CommandLine.Command(
        description = "Export content from MindTouch",
        name = "mindtouch-export", mixinStandardHelpOptions = true)
public class App implements Callable<Integer> {
    private static final String JOB_EXPORT_BODY = "<job><notification>" +
            "<email>hapiy.serhiy@gmail.com</email></notification>" +
//            "<email>volodymyr.kushnir@zoominsoftware.com</email></notification>" +
            "<pages includesubpages=\"true\"><page><path></path></page></pages></job>";
    private static final String JOBS_EXPORT_PATH = "/@api/deki/site/jobs/export";
    private static final String JOBS_PATH = "/@api/deki/site/jobs";
    public static final String JOB_STATUS_COMPLETED = "COMPLETED";
    public static final String USER = "=admin";

    private static final Logger logger = LoggerFactory.getLogger(App.class);
    private static final OkHttpClient okHttpClient = new OkHttpClient();

    @CommandLine.Option(names = {"-o", "--output"}, description = "Output directory", required = true)
    private File outputDir;

    @CommandLine.Option(names = {"-k", "--key"}, description = "Server token key", required = true)
    private String key;

    @CommandLine.Option(names = {"-s", "--secret"}, description = "Server token secret", required = true)
    private String secret;

    @CommandLine.Option(names = {"-u", "--host-url"}, description = "Host URL", required = true)
    private String hostUrl;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new App()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        try {
            logger.info("Creating export job");
            String jobId = createExportJob();
            logger.info("Waiting at most 10 minutes for export job to complete: {}", jobId);

            String downloadLink = await()
                    .atMost(10, MINUTES)
                    .pollInterval(Duration.ofSeconds(15))
                    .until(() -> jobIsComplete(jobId), Objects::nonNull);

            logger.info("Download link: {}", downloadLink);
            logger.info("I cannot download the archive yet, please help me");
            return 0;
        } catch (RuntimeException | IOException e) {
            logger.error("Export failed", e);
            return -1;
        }
    }

    @NotNull
    private String createExportJob() throws IOException {
        String token = generateToken();
        RequestBody body = RequestBody.create(JOB_EXPORT_BODY, MediaType.get("application/xml; charset=utf-8"));
        Request request = new Request.Builder()
                .header("X-Deki-Token", token)
                .post(body)
                .url(formatUrl(hostUrl, JOBS_EXPORT_PATH))
                .build();
        try (Response response = executeRequest(request)) {
            return extractJobId(response);
        }
    }

    private String jobIsComplete(String jobId) throws IOException {
        String token = generateToken();
        Request request = new Request.Builder()
                .header("X-Deki-Token", token)
                .get()
                .url(formatUrl(hostUrl, JOBS_PATH))
                .build();
        try (Response response = executeRequest(request)) {
            return extractDownloadUrl(response, jobId);
        }
    }

    @NotNull
    private Response executeRequest(Request request) {
        try {
            Response response = okHttpClient.newCall(request).execute();
            if (response.code() != 200) {
                logger.error(response.toString());
                logger.error(response.body().string());
                throw new RuntimeException("HTTP request failed");
            }
            return response;
        } catch (IOException e) {
            throw new RuntimeException("HTTP request failed", e);
        }
    }

    private String extractDownloadUrl(Response response, String jobId) {
        try {
            SAXReader reader = new SAXReader();
            try (InputStream is = response.body().byteStream()) {
                Document document = reader.read(is);
                Optional<Element> jobOptional = document.getRootElement()
                        .elements("job")
                        .stream()
                        .filter(el -> jobId.equals(el.attributeValue("id")))
                        .findFirst();
                if (!jobOptional.isPresent()) {
                    logger.warn("Job with id does not exist: {}", jobId);
                    return null;
                }
                Element job = jobOptional.get();
                String status = job.attributeValue("status");
                logger.info("Job status is: {}", status);
                if (JOB_STATUS_COMPLETED.equals(status)) {
                    logger.info("Done waiting");
                    return job.element("data").element("archive").getText();
                }
                return null;
            }
        } catch (IOException | DocumentException e) {
            throw new RuntimeException("Cannot extract download URL");
        }

    }

    private String extractJobId(Response response) {
        try {
            SAXReader reader = new SAXReader();
            try (InputStream is = response.body().byteStream()) {
                Document document = reader.read(is);
                return document.getRootElement().attributeValue("id");
            }
        } catch (IOException | DocumentException e) {
            throw new RuntimeException("Cannot extract job id");
        }
    }

    private String formatUrl(String host, String path) {
        return host.replace("/+$", "") + path;
    }

    @NotNull
    private String generateToken() {
        String epoch = Long.toString(new Date().getTime() / 1000L);
        try {
            Mac sha256Hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
            sha256Hmac.init(secretKey);
            String message = key + "_" + epoch + "_" + USER;
            String hash = Hex.encodeHexString(sha256Hmac.doFinal(message.getBytes()));
            return String.join("_", "tkn", key, epoch, USER, hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Cannot generate auth token", e);
        }
    }
}
