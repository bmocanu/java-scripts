public class HumbleDownload extends BaseDownload {

    public static void main(String[] args) throws Exception {
        new HumbleDownload().run(args);
    }

    // ----------------------------------------------------------------------------------------------------

    @Override
    String getAppName() {
        return "HumbleDownload";
    }

    @Override
    String getRegexPattern() {
        return "data-human-name=\"(?<name>[^\"]+)\".+?href=\"(?<link>https://dl\\.humble\\.com/[^\".]+\\.{extension}[^\".]+)";
    }

    @Override
    String getBaseUrl() {
        return "";
    }

}
