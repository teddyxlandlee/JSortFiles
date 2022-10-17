package xland.ioutils.jsortfiles;

public class Main {
    public static void main(String[] args) {

    }

    public static String help() {
        return  "Usage: jsortfiles [options]\n" +
                "Options:\n" +
                "\t-d, --source [directory]: the directory to be sorted, defaulting to working directory\n" +
                "\t-h, --help: print this help message\n" +
                "\t-m, --remove-after-sorting: remove the original files after sorting\n" +
                "\t-r, --recursive: to iterate the source directory recursively" +
                "\t-t, --target [directory]: target directory into which sorted files are moved, defaulting to source" +
                "\t-v, --version: print software version and copyright information\n";
    }

}
