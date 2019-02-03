public class GenDownload extends BaseDownload {

    public static void main(String[] args) throws Exception {
        new GenDownload().run(args);
    }

    // ----------------------------------------------------------------------------------------------------

    @Override
    String getAppName() {
        return "GenDownload";
    }

    @Override
    String getRegexPattern() {
        return "<td><a href=\"(?<link>[^\"]+)\">(?<name>[^<]+)</a></td>";
    }

    @Override
    String getBaseUrl() {
        return "";
    }

    @Override
    String getFormatsToDownloadAsCsv() {
        return "pdf";
    }
}
