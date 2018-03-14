package com.Acrobot.Breeze.Utils;

import com.Acrobot.Breeze.Collection.SimpleCache;
import com.Acrobot.ChestShop.ChestShop;
import com.Acrobot.ChestShop.Configuration.Properties;
import com.google.common.collect.ImmutableMap;
import de.themoep.ShowItem.api.ShowItem;
import info.somethingodd.OddItem.OddItem;
import org.bukkit.CoalType;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.TreeSpecies;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.material.*;
import org.bukkit.plugin.Plugin;
import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author Acrobot
 */
public class MaterialUtil {
    public static final Pattern DURABILITY = Pattern.compile(":(\\d)*");
    public static final Pattern METADATA = Pattern.compile("#([0-9a-zA-Z])*");

    public static final boolean LONG_NAME = true;
    public static final boolean SHORT_NAME = false;
    public static final short MAXIMUM_SIGN_LETTERS = 15;
    
    private static final SimpleCache<String, Material> MATERIAL_CACHE = new SimpleCache<>(Properties.CACHE_SIZE);

    /**
     * Checks if the itemStack is empty or null
     *
     * @param item Item to check
     * @return Is the itemStack empty?
     */
    public static boolean isEmpty(ItemStack item) {
        return item == null || item.getType() == Material.AIR;
    }

    /**
     * Checks if the itemStacks are equal, ignoring their amount
     *
     * @param one first itemStack
     * @param two second itemStack
     * @return Are they equal?
     */
    public static boolean equals(ItemStack one, ItemStack two) {
        if (one == null || two == null) {
            return one == two;
        }
        if (one.isSimilar(two)) {
            return true;
        }

        // Special check for books as their pages might change when serialising (See SPIGOT-3206)
        return one.getType() == two.getType()
                && one.getDurability() == two.getDurability()
                && one.getData().equals(two.getData())
                && one.hasItemMeta() && two.hasItemMeta()
                && one.getItemMeta() instanceof BookMeta && two.getItemMeta() instanceof BookMeta
                && one.getItemMeta().serialize().equals(two.getItemMeta().serialize());
    }

    /**
     * Gives you a Material from a String (doesn't have to be fully typed in)
     *
     * @param name Name of the material
     * @return Material found
     */
    public static Material getMaterial(String name) {
        String formatted = name.toUpperCase();

        Material material = MATERIAL_CACHE.get(formatted);
        if (material != null) {
            return material;
        }

        material = Material.matchMaterial(name);

        if (material != null) {
            MATERIAL_CACHE.put(formatted, material);
            return material;
        }
        
        String[] nameParts = name.toUpperCase().split(" |_");

        short length = Short.MAX_VALUE;

        for (Material currentMaterial : Material.values()) {
            String matName = currentMaterial.toString();

            if (matName.length() < length && matName.startsWith(formatted)) {
                length = (short) matName.length();
                material = currentMaterial;
            } else if (nameParts.length > 1) {
                String[] matParts = matName.split("_");
                if (nameParts.length == matParts.length) {
                    boolean matched = true;
                    for (int i = 0; i < matParts.length; i++) {
                        if (!matParts[i].startsWith(nameParts[i])) {
                            matched = false;
                            break;
                        }
                    }
                    if (matched) {
                        material = currentMaterial;
                        break;
                    }
                }
            }
        }

        MATERIAL_CACHE.put(formatted, material);

        return material;
    }

    /**
     * Returns item's name
     *
     * @param itemStack ItemStack to name
     * @return ItemStack's name
     */
    public static String getName(ItemStack itemStack) {
        return getName(itemStack, 0);
    }

    /**
     * Returns item's name
     *
     * @param itemStack     ItemStack to name
     * @param showDataValue Should we also show the data value?
     * @return ItemStack's name
     * @deprecated Use {@link #getName(ItemStack, int)}
     */
    @Deprecated
    public static String getName(ItemStack itemStack, boolean showDataValue) {
        String dataName = DataValue.name(itemStack);

        if (dataName != null && showDataValue) {
            return StringUtil.capitalizeFirstLetter(dataName + '_' + itemStack.getType(), '_');
        } else {
            return StringUtil.capitalizeFirstLetter(itemStack.getType().toString(), '_');
        }
    }

    /**
     * Returns item's name, just like on the sign
     *
     * @param itemStack ItemStack to name
     * @return ItemStack's name
     */
    public static String getSignName(ItemStack itemStack) {
        return getName(itemStack, MAXIMUM_SIGN_LETTERS);
    }
    
    /**
     * Returns item's name, with a maximum length
     *
     * @param itemStack ItemStack to name
     * @param maxLength The max length that the name should have; 0 or below if it should be unlimited
     * @return ItemStack's name
     */
    public static String getName(ItemStack itemStack, int maxLength) {
        String alias = Odd.getAlias(itemStack);
        String itemName = alias != null ? alias : itemStack.getType().toString();
    
        String data = DataValue.name(itemStack);
        String durability = "";
        if (data == null) {
            if (itemStack.getDurability() != 0) {
                durability = ":" + itemStack.getDurability();
            }
        }
        data = data != null ? data + "_" : "";

        String metaData = "";
        if (itemStack.hasItemMeta()) {
            metaData = "#" + Metadata.getItemCode(itemStack);
        }
    
        String code = data + itemName + durability + metaData;
        if (maxLength > 0 && code.length() > maxLength) {
            int exceeding = code.length() - maxLength;
            code = data + getShortenedName(itemName, itemName.length() - exceeding) + durability + metaData;
        }

        code = StringUtil.capitalizeFirstLetter(code, '_');
        
        ItemStack codeItem = getItem(code);
        if (!equals(itemStack, codeItem)) {
            throw new IllegalArgumentException("Cannot generate code for item " + itemStack + " with maximum length of " + maxLength);
        }

        return code;
    }
    
    /**
     * Get an item name shortened to a max length that is still reversable by {@link #getMaterial(String)}
     * @param itemName  The name of the item
     * @param maxLength The max length
     * @return The name shortened to the max length
     */
    public static String getShortenedName(String itemName, int maxLength) {
        if (itemName.length() <= maxLength) {
            return itemName;
        }
        int exceeding = itemName.length() - maxLength;
        String[] itemParts = itemName.split("_");
        int shortestIndex = 0;
        int longestIndex = 0;
        for (int i = 0; i < itemParts.length; i++) {
            if (itemParts[longestIndex].length() < itemParts[i].length()) {
                longestIndex = i;
            }
            if (itemParts[shortestIndex].length() > itemParts[i].length()) {
                shortestIndex = i;
            }
        }
        if (itemParts[longestIndex].length() - itemParts[shortestIndex].length() > exceeding) {
            itemParts[longestIndex] = itemParts[longestIndex].substring(0, itemParts[longestIndex].length() - exceeding);
        } else {
            for (int i = itemParts.length - 1; i >= 0 && exceeding > 0; i--) {
                int remove = 0;
                if (itemParts[i].length() > itemParts[shortestIndex].length()) {
                    remove = itemParts[i].length() - itemParts[shortestIndex].length();
                }
                if (remove > exceeding) {
                    remove = exceeding;
                }
                itemParts[i] = itemParts[i].substring(0, itemParts[i].length() - remove);
                exceeding -= remove;
            }
            while (exceeding > 0) {
                for (int i = itemParts.length - 1; i >= 0 && exceeding > 0; i--) {
                    itemParts[i] = itemParts[i].substring(0, itemParts[i].length() - 1);
                    exceeding--;
                }
            }
        }
        return String.join("_", itemParts);
    }
    
    /**
     * Gives you an ItemStack from a String
     *
     * @param itemName Item name
     * @return ItemStack
     */
    public static ItemStack getItem(String itemName) {
        ItemStack itemStack = Odd.getFromString(itemName);

        if (itemStack != null) {
            return itemStack;
        }

        String[] split = itemName.split("[:\\-#]");
        for (int i = 0; i < split.length; i++) {
            split[i] = split[i].trim();
        }

        Material material = getMaterial(split[0]);
        short durability = getDurability(itemName);

        if (material == null) {
            if (!split[0].contains(" ")) {
                return null;
            }

            for (int index = split[0].indexOf(' '); index >= 0 && index + 1 < split[0].length(); index = split[0].indexOf(' ', index + 1)) {
                material = getMaterial(split[0].substring(index + 1));

                if (material != null) {
                    if (durability == 0) {
                        durability = DataValue.get(split[0].substring(0, index), material);
                    }

                    break;
                }
            }

            if (material == null) {
                return null;
            }
        }

        itemStack = new ItemStack(material);
        itemStack.setDurability(durability);

        ItemMeta meta = getMetadata(itemName);

        if (meta != null) {
            itemStack.setItemMeta(meta);
        }

        return itemStack;
    }

    /**
     * Returns the durability from a string
     *
     * @param itemName Item name
     * @return Durability found
     */
    public static short getDurability(String itemName) {
        Matcher m = DURABILITY.matcher(itemName);

        if (!m.find()) {
            return 0;
        }

        String data = m.group();

        if (data == null || data.isEmpty()) {
            return 0;
        }

        data = data.substring(1);

        return NumberUtil.isShort(data) ? Short.valueOf(data) : 0;
    }

    /**
     * Returns metadata from a string
     *
     * @param itemName Item name
     * @return Metadata found
     */
    public static ItemMeta getMetadata(String itemName) {
        Matcher m = METADATA.matcher(itemName);

        if (!m.find()) {
            return null;
        }

        String group = m.group().substring(1);
        return Metadata.getFromCode(group);
    }

    //1.13 TODO: Get rid of numeric data values with the API that replaces MaterialData
    public static class DataValue {
        /**
         * Gets the data value from a string
         *
         * @param type     Data Value string
         * @param material Material
         * @return data value
         */
        public static byte get(String type, Material material) {
            if (material == null || material.getData() == null) {
                return 0;
            }

            type = type.toUpperCase().replace(" ", "_");

            MaterialData materialData = material.getNewData((byte) 0);

            if (materialData instanceof TexturedMaterial) {
                TexturedMaterial texturedMaterial = (TexturedMaterial) materialData;

                for (Material mat : texturedMaterial.getTextures()) {
                    if (mat.name().startsWith(type) && !mat.equals(material)) {
                        return (byte) texturedMaterial.getTextures().indexOf(mat);
                    }
                }
            } else if (materialData instanceof Colorable) {
                DyeColor color;

                try {
                    color = DyeColor.valueOf(type);
                } catch (IllegalArgumentException exception) {
                    return 0;
                }

                if (material == Material.INK_SACK) {
                    return color.getDyeData();
                }

                return color.getWoolData();
            } else if (materialData instanceof Tree) {
                try {
                    return TreeSpecies.valueOf(type).getData();
                } catch (IllegalArgumentException ex) {
                    return 0;
                }
            } else if (materialData instanceof SpawnEgg) {
                try {
                    EntityType entityType = EntityType.valueOf(type);

                    return (byte) entityType.getTypeId();
                } catch (IllegalArgumentException ex) {
                    return 0;
                }
            } else if (materialData instanceof Coal) {
                try {
                    return CoalType.valueOf(type).getData();
                } catch (IllegalArgumentException ex) {
                    return 0;
                }
            }

            return 0;
        }

        /**
         * Returns a string with the DataValue
         *
         * @param itemStack ItemStack to describe
         * @return Data value string
         */
        public static String name(ItemStack itemStack) {
            MaterialData data = itemStack.getData();

            if (data == null) {
                return null;
            }

            if (data instanceof TexturedMaterial) {
                return ((TexturedMaterial) data).getMaterial().name();
            } else if (data instanceof Colorable) {
                DyeColor color = ((Colorable) data).getColor();

                return (color != null ? color.name() : null);
            } else if (data instanceof Tree) {
                //TreeSpecies specie = TreeSpecies.getByData((byte) (data.getData() & 3)); //This works, but not as intended
                TreeSpecies specie = ((Tree) data).getSpecies();
                return (specie != null && specie != TreeSpecies.GENERIC ? specie.name() : null);
            } else if (data instanceof SpawnEgg) {
                EntityType type = ((SpawnEgg) data).getSpawnedType();
                return (type != null ? type.name() : null);
            } else if (data instanceof Coal) {
                CoalType coal = ((Coal) data).getType();
                return (coal != null && coal != CoalType.COAL ? coal.name() : null);
            } else {
                return null;
            }
        }
    }

    public static class Metadata {
        /**
         * Returns the ItemMeta represented by this code
         *
         * @param code Code representing the ItemMeta
         * @return ItemMeta represented by code
         */
        public static ItemMeta getFromCode(String code) {
            ItemStack item = ChestShop.getItemDatabase().getFromCode(code);

            if (item == null) {
                return null;
            } else {
                return item.getItemMeta();
            }
        }

        /**
         * Returns the code for this item
         *
         * @param item Item being represented
         * @return Code representing the item
         */
        public static String getItemCode(ItemStack item) {
            return ChestShop.getItemDatabase().getItemCode(item);
        }
    }

    public static class Odd {
        private static boolean isInitialized = false;

        /**
         * Returns the item stack from OddItem plugin
         *
         * @param itemName Item name to parse
         * @return itemStack that was parsed
         */
        public static ItemStack getFromString(String itemName) {
            if (!isInitialized) {
                return null;
            }

            String name = itemName.replace(':', ';');

            try {
                return OddItem.getItemStack(name);
            } catch (Exception ex) {
                return null;
            }
        }

        public static String getAlias(ItemStack itemStack) {
            if (!isInitialized) {
                return null;
            }

            try {
                Collection<String> aliases = OddItem.getAliases(itemStack);
                if (!aliases.isEmpty()) {
                    return aliases.iterator().next();
                }
            } catch (Exception ignored) {}
            return null;
        }

        /**
         * Lets the class know that it's safe to use the OddItem methods now
         */
        public static void initialize() {
            isInitialized = true;
        }
    }
    
    public static class Show {
        private static ShowItem showItem = null;
        
        /**
         * Lets the class know that it's safe to use the ShowItem methods now
         *
         * @param plugin
         */
        public static void initialize(Plugin plugin) {
            showItem = (ShowItem) plugin;
        }
    
        /**
         * Send a message with hover info and icons
         *
         * @param player  The player to send the message to
         * @param message The raw message
         * @param stock   The items in stock
         */
        public static boolean sendMessage(Player player, String message, ItemStack[] stock) {
            if (showItem == null) {
                return false;
            }
            
            List<String> itemJson = new ArrayList<>();
            for (ItemStack item : InventoryUtil.mergeSimilarStacks(stock)) {
                try {
                    itemJson.add(showItem.getItemConverter().createComponent(item, Level.OFF).toJsonString(player));
                } catch (Exception e) {
                    ChestShop.getPlugin().getLogger().log(Level.WARNING, "Error while trying to send message '" + message + "' to player " + player.getName() + ": " + e.getMessage());
                    return false;
                }
            }
            
            String joinedItemJson = itemJson.stream().collect(Collectors.joining("," + new JSONObject(ImmutableMap.of("text", " ")).toJSONString() + ", "));
            
            String messageJsonString = Arrays.stream(message.split("%item"))
                    .map(s -> new JSONObject(ImmutableMap.of("text", s)).toJSONString())
                    .collect(Collectors.joining("," + joinedItemJson + ","));
    
            while (messageJsonString.startsWith(",")) {
                messageJsonString = messageJsonString.substring(1);
            }
            while (messageJsonString.endsWith(",")) {
                messageJsonString = messageJsonString.substring(0, messageJsonString.length() - 1);
            }
            
            showItem.tellRaw(player, messageJsonString);
            return true;
        }
    }
}
