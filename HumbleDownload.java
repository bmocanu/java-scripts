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

public class HumbleDownload {

    public static void main(String[] args) throws Exception {
        System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tH:%1$tM:%1$tS.%1$tL %4$-10s %5$s %6$s%n");
        String inputFileName = System.getProperty("user.dir") + "/page.html";
        String outputDir = System.getProperty("user.dir") + "/output";
        String formatsToGet = "pdf,mobi,epub,cbz";
        int startAt = 0;
        boolean list = false;
        boolean dry = false;
        boolean download = false;

        if (args != null && args.length > 0) {
            for (int index = 0; index < args.length; index++) {
                if ("-list".equalsIgnoreCase(args[index])) {
                    list = true;
                }
                if ("-dry".equalsIgnoreCase(args[index])) {
                    dry = true;
                }
                if ("-download".equalsIgnoreCase(args[index])) {
                    download = true;
                }
                if ("-get".equalsIgnoreCase(args[index])) {
                    formatsToGet = args[index + 1];
                }
                if ("-start".equalsIgnoreCase(args[index])) {
                    startAt = Integer.parseInt(args[index + 1]);
                }
                if ("-input".equalsIgnoreCase(args[index])) {
                    inputFileName = args[index + 1];
                }
                if ("-output".equalsIgnoreCase(args[index])) {
                    outputDir = args[index + 1];
                }
            }
        }

        if (!list && !dry && !download) {
            printHelp();
            return;
        }

        List<Item> items = loadItems(inputFileName, formatsToGet);
        listItems(items);
        if (dry) {
            dryItems(items, startAt);
        }
        if (download) {
            downloadItems(items, startAt, outputDir);
        }
        LOG.log(INFO, "Job done!");
    }

    // ----------------------------------------------------------------------------------------------------

    private static final Logger LOG = Logger.getLogger(HumbleDownload.class.getName());

    private static class Item {
        String name;
        String link;
    }

    private static void printHelp() {
        System.out.println("=============================");
        System.out.println("Humble Bundle download script");
        System.out.println("=============================");
        System.out.println("java HumbleDownload [-list] [-dry] [-download] [-start X] [-get pdf,moby,epub,cbz] [-input HTMLfile] [-output outputdir]");
        System.out.println("  -list: just parse the HTML file and list the items found");
        System.out.println("  -dry: do not download the items, but just HTTP HEAD them, to ensure a download would work");
        System.out.println("  -download: do download the items");
        System.out.println("  -start X: start with the item index X (run with -list to just list the items, with their index)");
        System.out.println("  -get format1,format2,...: download only the specified formats. Any number of extensions is possible");
        System.out.println("  -input file: the input HTML fragment file. Default is page.html, in current dir");
        System.out.println("  -output dir: the output directory. Default is ./output, in current dir. It is created if it is not found");
        System.out.println("=============================");
        System.out.println("How to  use: ");
        System.out.println("  1. BUY the desired Humble Bundle book or comic bundle");
        System.out.println("  2. Go to the \"Get your books\" page");
        System.out.println("  3. Open Developer Tools on your browser, copy the HTML fragment containing all the items");
        System.out.println("  4. Paste the HTML fragment into a file called \"page.html\" or whatever (check usage above)");
        System.out.println("  5. Run the HumbleDownloader with -list, to make sure all items are recognized");
        System.out.println("  6. (optional) Run the HumbleDownloader with -dry, to make sure all items can be downloaded");
        System.out.println("  7. Run the HumbleDownloader with -download, to download all items");
        System.out.println("=============================");
        System.out.println("Default settings:");
        System.out.println("  - input file:    ./page.html");
        System.out.println("  - output folder: ./output");
        System.out.println("  - start:         0");
        System.out.println("  - get:           pdf,mobi,epub,cbz");
        System.out.println("=============================");
    }

    private static List<Item> loadItems(String fileName, String formatsCsv) throws Exception {
        String linkPatternFormat = "data-human-name=\"([^\"]+)\".+?href=\"(https://dl\\.humble\\.com/[^\".]+\\.{extension}[^\".]+)";
        String[] formats = formatsCsv.split(",");
        LOG.log(INFO, "Loading download items from: {0}, with formats: {1}", new Object[]{fileName, Arrays.toString(formats)});
        String inputFileContent = loadFile(fileName);
        List<Item> resultList = new ArrayList<>();
        for (String format : formats) {
            int itemsCount = 0;
            Pattern pattern = Pattern.compile(linkPatternFormat.replace("{extension}", format.trim()));
            Matcher linkPatternMatcher = pattern.matcher(inputFileContent);
            while (linkPatternMatcher.find()) {
                Item newItem = new Item();
                newItem.name = normalizeFileName(linkPatternMatcher.group(1)) + "." + format;
                newItem.link = normalizeLink(linkPatternMatcher.group(2));
                resultList.add(newItem);
                itemsCount++;
            }
            LOG.log(INFO, "Found {0} items with extension: {1}", new Object[]{itemsCount, format});
        }
        return resultList;
    }

    private static String normalizeFileName(String fileName) {
        String result = fileName;
        result = result.replaceAll("&amp;", "&");
        result = result.replaceAll(":", " - ");
        result = result.replaceAll("\\s+", " ");
        return result;
    }

    private static String normalizeLink(String link) {
        String result = link;
        result = result.replaceAll("&amp;", "&");
        return result;
    }

    private static void listItems(List<Item> items) {
        LOG.log(INFO, "Printing {0} item(s)", items.size());
        for (int index = 0; index < items.size(); index++) {
            Item item = items.get(index);
            LOG.log(INFO, "{0} - Item [{1}] ==> [{2}]", new Object[]{index, item.name, item.link});
        }
    }

    private static String loadFile(String fileName) throws Exception {
        try (BufferedReader reader = new BufferedReader(new FileReader(new File(fileName)))) {
            StringBuilder builder = new StringBuilder();
            for (String line; (line = reader.readLine()) != null; ) {
                builder.append(line);
            }
            return builder.toString();
        }
    }

    private static void dryItems(List<Item> items, int startAt) throws Exception {
        LOG.log(INFO, "Probing {0} item(s)", items.size() - startAt);
        System.setProperty("http.keepAlive", "false");
        for (int index = startAt; index < items.size(); index++) {
            Item item = items.get(index);
            int statusCode;

            HttpURLConnection connection = null;
            try {
                URL url = new URL(item.link);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("HEAD");
                statusCode = connection.getResponseCode();
                connection.getInputStream().close();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
            LOG.log(INFO, "{0} - HEAD [{1}] => HTTP {2}", new Object[]{index, item.name, statusCode});
        }
    }

    private static void downloadItems(List<Item> items, int startAt, String outputDir) throws Exception {
        File outputDirObject = new File(outputDir);
        if (!outputDirObject.exists()) {
            LOG.log(INFO, "Directory [{0}] does not exist. Creating it", outputDir);
            if (outputDirObject.mkdirs()) {
                LOG.log(INFO, "Directory [{0}] created successfully", outputDir);
            } else {
                LOG.log(SEVERE, "Directory [{0}] was NOT created entirely (some subdirs might have been created)", outputDir);
                return;
            }
        }

        LOG.log(INFO, "Downloading {0} item(s) to [{1}]", new Object[]{items.size() - startAt, outputDir});
        System.setProperty("http.keepAlive", "false");
        for (int index = startAt; index < items.size(); index++) {
            Item item = items.get(index);
            int statusCode;
            LOG.log(INFO, "{0} - GET [{1}]", new Object[]{index, item.name});

            HttpURLConnection connection = null;
            try {
                URL url = new URL(item.link);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                statusCode = connection.getResponseCode();
                if (statusCode == 200) {
                    File outputFile = new File(outputDir + "/" + item.name);
                    try (BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(outputFile)); InputStream inputStream = connection.getInputStream()) {
                        byte[] buffer = new byte[1024 * 1024];
                        int readBytes;
                        while ((readBytes = inputStream.read(buffer)) > 0) {
                            outputStream.write(buffer, 0, readBytes);
                        }
                    }
                }
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
    }

}
