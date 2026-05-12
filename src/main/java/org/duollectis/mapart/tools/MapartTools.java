package org.duollectis.mapart.tools;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;
import org.duollectis.mapart.tools.commands.ListCommand;
import org.duollectis.mapart.tools.utils.FileUtils;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.FormattingStyle;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
    name = "mapart-tools",
    description = "",
    version = "1.0",
    mixinStandardHelpOptions = true,
    subcommands = {ListCommand.class})
public class MapartTools implements Runnable {

    public static final File DATA_DIR = new File("./data");

    private static final String BLOCK_MAP_NAME = "block_map.json";
    private static final String COLOR_MAP_NAME = "color_map.json";

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .setFormattingStyle(FormattingStyle.PRETTY.withIndent("    "))
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .serializeNulls()
        .create();

    // Options

    @Option(
        names = {"-v", "--version"},
        description = "The target Minecraft version (matches the data folder name).")
    private String version;

    //

    private final Map<Integer, Integer> colorMap = new HashMap<>();
    private final Map<Integer, Integer> blockMap = new HashMap<>();

    @Override
    public void run() {
        initDir();
        initData();
    }

    private void initData() {
        File versionDir = FileUtils.getFileOrExit(
            DATA_DIR,
            version,
            true,
            "Version '%s' not found!".formatted(version));

        File blockMapFile = FileUtils.getFileOrExit(
            versionDir,
            BLOCK_MAP_NAME,
            false,
            "File '%s' from '%s' not found!".formatted(BLOCK_MAP_NAME, version));


        File colorMapFile = FileUtils.getFileOrExit(
            versionDir,
            COLOR_MAP_NAME,
            false,
            "File '%s' from '%s' not found!".formatted(COLOR_MAP_NAME, version));

        if (blockMapFile == null || colorMapFile == null) {
            return;
        }

        try {
            colorMap.putAll(GSON.fromJson(
                new FileReader(colorMapFile),
                new TypeToken<Map<Integer, Integer>>() {}.getType()));

            blockMap.putAll(GSON.fromJson(
                new FileReader(blockMapFile),
                new TypeToken<Map<Integer, Integer>>() {}.getType()));
        } catch (JsonIOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (JsonSyntaxException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private static void initDir() {
        if (!DATA_DIR.exists() && !DATA_DIR.mkdirs()) {
            System.err.println("Can't create a data dir!");
            System.exit(-1);
            return;
        }
    }

    public static void main(String[] args) {
        System.exit(new CommandLine(new MapartTools()).execute(args));
    }
}
