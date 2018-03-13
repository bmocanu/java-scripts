import java.io.File;

public class FolderTreeDelete {

    public static String startFolderString = "D:\\Temp\\test22";

    public static void main(String[] args) {
        listAndRemove(new File(startFolderString));
    }

    private static void listAndRemove(File parent) {
        System.out.println("Entering " + parent.getAbsolutePath());
        File[] children = parent.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) {
                    listAndRemove(child);
                }
                System.out.print("Removing " + child.getAbsolutePath() + " ==> ");
                if (child.delete()) {
                    System.out.println("OK");
                } else {
                    System.out.println("ERROR");
                }
            }
        }
    }

}
