import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.SEVERE;

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
    String getUrlBase() {
        return "";
    }

}
