package ca.xef5000.plugin.flags;

import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.FlagContext;
import com.sk89q.worldguard.protection.flags.InvalidFlagFormat;
import com.sk89q.worldguard.protection.flags.RegionGroup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class ListStringFlag extends Flag<List<String>> {
    protected ListStringFlag(String name, RegionGroup defaultGroup) {
        super(name, defaultGroup);
    }

    public ListStringFlag(String name) {
        super(name);
    }

    @Override
    public List<String> parseInput(FlagContext flagContext) throws InvalidFlagFormat {
        String input = flagContext.getUserInput();
        if (input == null || input.trim().isEmpty()) {
            throw new InvalidFlagFormat("Empty or null input for list flag");
        }
        // Split on commas, allowing optional surrounding whitespace
        String[] parts = input.split("\\s*,\\s*");
        return Arrays.asList(parts);
    }

    @Override
    public List<String> unmarshal(Object o) {
        if (o instanceof List) {
            List<?> raw = (List<?>) o;
            List<String> result = new ArrayList<>();
            for (Object elem : raw) {
                result.add(Objects.toString(elem, ""));
            }
            return result;
        }
        if (o instanceof String) {
            return Arrays.asList(((String) o).split("\\s*,\\s*"));
        }
        return null;
    }

    @Override
    public Object marshal(List<String> list) {
        // Keeping it as a list object; WG will serialize as a YAML list
        return list;
    }
}
