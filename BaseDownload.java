import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveAction;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.SEVERE;

abstract class BaseDownload {

    // Extension points -----------------------------------------------------------------------------------

    /**
     * @return the name of the app. It is displayed in various help messages and log output
     */
    abstract String getAppName();

    /**
     * Returns the pattern that identifies the links to be downloaded and the name of the files. Make sure
     * the pattern has the following two capturing groups: "name" and "link". You can also use the
     * "{extension}" token, to have the extension dynamically replaced before regex compilation and matching.
     *
     * @return the pattern that identifies the links to be downloaded and the name of the files
     */
    abstract String getRegexPattern();

    /**
     * @return the base of the URL, in case the links are relative to the HTML page. Just return the empty string
     * if the URL is already absolute (do not return NULL).
     */
    abstract String getBaseUrl();

    /**
     * @return the CSV of formats that should be downloaded by default, if the user does not override this
     * with a config flag
     */
    abstract String getFormatsToDownloadAsCsv();

    // ----------------------------------------------------------------------------------------------------

    private static final Logger LOG = Logger.getLogger(BaseDownload.class.getName());

    private String outputDir;

    void run(String[] args) throws Exception {
        System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tH:%1$tM:%1$tS.%1$tL %4$-10s %5$s %6$s%n");
        String inputFileName = System.getProperty("user.dir") + "/page.html";
        outputDir = System.getProperty("user.dir") + "/output";
        String formatsToGet = getFormatsToDownloadAsCsv();

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
            dryItems(items);
        }
        if (download) {
            downloadItems(items, outputDir);
        }
        LOG.log(INFO, "Job done!");
    }

    // ----------------------------------------------------------------------------------------------------

    private void printHelp() {
        String appName = getAppName();
        System.out.println("==========================================================");
        System.out.println(appName + " download script");
        System.out.println("==========================================================");
        System.out.println("java " + appName + " [-list] [-dry] [-download] [-get pdf,moby,epub,cbz] [-input HTMLfile] [-output outputdir]");
        System.out.println("  -list: just parse the HTML file and list the items found");
        System.out.println("  -dry: do not download the items, but just HTTP HEAD them, to ensure a download would work");
        System.out.println("  -download: do download the items");
        System.out.println("  -get format1,format2,...: download only the specified formats. Any number of extensions is possible");
        System.out.println("  -input file: the input HTML fragment file. Default is page.html, in current dir");
        System.out.println("  -output dir: the output directory. Default is ./output, in current dir. It is created if it is not found");
        System.out.println("==========================================================");
        System.out.println("How to  use: ");
        System.out.println("  1. Go to the listing of the files you want to download");
        System.out.println("  2. Open Developer Tools on your browser, copy the HTML fragment containing all the items");
        System.out.println("  3. Paste the HTML fragment into a file called \"page.html\" or whatever (check usage above)");
        System.out.println("  4. Run the " + appName + " with -list, to make sure all items are recognized");
        System.out.println("  5. (optional) Run the " + appName + " with -dry, to make sure all items can be downloaded");
        System.out.println("  6. Run the " + appName + " with -download, to download all items");
        System.out.println("  7. If you stop the download at any point, just run it again, and it will skip all downloaded files");
        System.out.println("==========================================================");
        System.out.println("Default settings:");
        System.out.println("  - input file:    ./page.html");
        System.out.println("  - output folder: ./output");
        System.out.println("  - get:           " + getFormatsToDownloadAsCsv());
        System.out.println("==========================================================");
    }

    private List<Item> loadItems(String fileName, String formatsCsv) throws Exception {
        String linkPatternFormat = getRegexPattern();

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
                newItem.name = normalizeFileName(linkPatternMatcher.group("name"), format);
                newItem.link = normalizeLink(linkPatternMatcher.group("link"));
                resultList.add(newItem);
                itemsCount++;
            }
            LOG.log(INFO, "Found {0} items with extension: {1}", new Object[]{itemsCount, format});
        }
        return resultList;
    }

    private String normalizeFileName(String fileName, String extension) {
        String result = fileName;
        result = result.replaceAll("&amp;", "&");
        result = result.replaceAll("[:\\\\/]", " - ");
        result = result.replaceAll("\\s+", " ");
        result = result.trim();

        if (!result.endsWith("." + extension)) {
            result = result + "." + extension;
        }

        result = result.replace("..", ".");

        return result;
    }

    private String normalizeLink(String link) {
        String result = link;
        result = result.replaceAll("&amp;", "&");
        return result;
    }

    private void listItems(List<Item> items) {
        LOG.log(INFO, "Printing {0} item(s)", items.size());
        for (int index = 0; index < items.size(); index++) {
            Item item = items.get(index);
            LOG.log(INFO, "{0} - Item [{1}] ==> [{2}]", new Object[]{index, item.name, item.link});
        }
    }

    private String loadFile(String fileName) throws Exception {
        try (BufferedReader reader = new BufferedReader(new FileReader(new File(fileName)))) {
            StringBuilder builder = new StringBuilder();
            for (String line; (line = reader.readLine()) != null; ) {
                builder.append(line);
            }
            return builder.toString();
        }
    }

    private void dryItems(List<Item> items) throws Exception {
        LOG.log(INFO, "Probing {0} item(s)", items.size());
        System.setProperty("http.keepAlive", "false");
        for (int index = 0; index < items.size(); index++) {
            Item item = items.get(index);
            int statusCode;

            HttpURLConnection connection = null;
            try {
                URL url = new URL(combineBaseUrlWithPath(getBaseUrl(), item.link));
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

    private void downloadItems(List<Item> items, String outputDir) {
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

        LOG.log(INFO, "Downloading {0} item(s) to [{1}]", new Object[]{items.size(), outputDir});
        System.setProperty("http.keepAlive", "false");

        ForkJoinPool commonPool = ForkJoinPool.commonPool();
        DownloadTask downloadTask = new DownloadTask(this, items.toArray(new Item[0]));
        commonPool.execute(downloadTask);
        downloadTask.join();
    }

    private String getOutputDir() {
        return outputDir;
    }

    // ----------------------------------------------------------------------------------------------------

    private static class DownloadTask extends RecursiveAction {

        private Item[] items;

        private int downloadLimit = 50;

        private BaseDownload instance;

        DownloadTask(BaseDownload instance, Item[] items) {
            this.instance = instance;
            this.items = items;
        }

        @Override
        protected void compute() {
            if (items.length > downloadLimit) {
                ForkJoinTask.invokeAll(splitInHalf());
            } else {
                downloadItems();
            }
        }

        private Collection<DownloadTask> splitInHalf() {
            List<DownloadTask> dividedTasks = new ArrayList<>();
            dividedTasks.add(new DownloadTask(instance, Arrays.copyOfRange(items, 0, items.length / 2)));
            dividedTasks.add(new DownloadTask(instance, Arrays.copyOfRange(items, items.length / 2, items.length)));
            return dividedTasks;
        }

        private void downloadItems() {
            for (int index = 0; index < items.length; index++) {
                Item item = items[index];

                File outputFile = new File(instance.getOutputDir() + "/" + item.name);
                if (outputFile.exists()) {
                    LOG.log(INFO, "SKIPPING [{0}], it already exists", new Object[]{item.name});
                } else {
                    LOG.log(INFO, "GET [{0}]", new Object[]{item.name});
                    HttpURLConnection connection = null;
                    try {
                        URL url = new URL(combineBaseUrlWithPath(instance.getBaseUrl(), item.link));
                        connection = (HttpURLConnection) url.openConnection();
                        connection.setRequestMethod("GET");
                        if (connection.getResponseCode() == 200) {
                            try (BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(outputFile)); InputStream inputStream = connection.getInputStream()) {
                                byte[] buffer = new byte[1024 * 1024 * 10];
                                int readBytes;
                                while ((readBytes = inputStream.read(buffer)) > 0) {
                                    outputStream.write(buffer, 0, readBytes);
                                }
                            }
                        }
                    } catch (Exception e) {
                        LOG.log(SEVERE, "Failed to download file {0}", new Object[]{item.name});
                        e.printStackTrace();
                    } finally {
                        if (connection != null) {
                            connection.disconnect();
                        }
                    }
                }
            }
        }

    }

    // ----------------------------------------------------------------------------------------------------

    private static String combineBaseUrlWithPath(String baseUrl, String path) {
        if (baseUrl == null || baseUrl.length() == 0) {
            return path.trim();
        }
        String trimmedBaseUrl = baseUrl.trim();
        String trimmedPath = path.trim();
        if (!trimmedBaseUrl.endsWith("/") && !trimmedPath.startsWith("/")) {
            return trimmedBaseUrl + "/" + trimmedPath;
        }
        return trimmedBaseUrl + trimmedPath;
    }

    protected static class Item {
        String name;
        String link;
    }

}
