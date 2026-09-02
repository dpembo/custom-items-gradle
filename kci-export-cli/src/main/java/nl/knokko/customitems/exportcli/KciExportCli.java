package nl.knokko.customitems.exportcli;

import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;
import nl.knokko.customitems.MCVersions;
import nl.knokko.customitems.bithelper.ByteArrayBitOutput;
import nl.knokko.customitems.editor.resourcepack.ResourcepackGenerator;
import nl.knokko.customitems.item.KciArmor;
import nl.knokko.customitems.item.KciHoe;
import nl.knokko.customitems.item.KciAttributeModifier;
import nl.knokko.customitems.item.KciItemType;
import nl.knokko.customitems.item.KciTool;
import nl.knokko.customitems.item.KciShears;
import nl.knokko.customitems.item.enchantment.LeveledEnchantment;
import nl.knokko.customitems.item.enchantment.VEnchantmentType;
import nl.knokko.customitems.itemset.ItemSet;
import nl.knokko.customitems.settings.ExportSettings;
import nl.knokko.customitems.texture.KciTexture;
import nl.knokko.customitems.texture.ArmorTexture;
import nl.knokko.customitems.util.StringEncoder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KciExportCli {

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && "--help".equals(args[0])) {
            System.out.println("Usage: kci-export-cli <items-directory> <textures-directory> <output.cis.txt> <resource-pack.zip>");
            return;
        }
        if (args.length != 4) {
            System.err.println("Expected item directory, texture directory, configuration output, and resource-pack output. Use --help for usage.");
            System.exit(64);
        }

        Path itemsDirectory = Path.of(args[0]);
        Path texturesDirectory = Path.of(args[1]);
        Path outputFile = Path.of(args[2]);
        Path resourcePackFile = Path.of(args[3]);
        if (!Files.isDirectory(itemsDirectory) || !Files.isDirectory(texturesDirectory)) {
            System.err.println("Item or texture directory does not exist.");
            System.exit(66);
        }

        ItemSet itemSet = new ItemSet(ItemSet.Side.EDITOR);
        ExportSettings exportSettings = new ExportSettings(true);
        exportSettings.setMcVersion(MCVersions.VERSION26);
        exportSettings.setMode(ExportSettings.Mode.MANUAL);
        exportSettings.setSkipResourcepack(false);
        itemSet.setExportSettings(exportSettings);
        Map<String, ArmorTexture> armorTextures = new HashMap<>();

        for (Path itemFile : Files.list(itemsDirectory).filter(path -> path.toString().endsWith(".json")).sorted().toList()) {
            JsonObject itemJson = Jsoner.deserialize(Files.readString(itemFile), new JsonObject());
            String itemName = requireString(itemJson, "id", itemFile);
            String texturePath = requireString(itemJson, "texture", itemFile);
            KciItemType itemType = parseItemType(requireString(itemJson, "material", itemFile), itemFile);

            Path imagePath = texturesDirectory.resolve(texturePath).normalize();
            if (!imagePath.startsWith(texturesDirectory) || !Files.isRegularFile(imagePath)) {
                throw new IllegalArgumentException(itemFile + " refers to missing texture " + texturePath);
            }
            BufferedImage image = ImageIO.read(imagePath.toFile());
            if (image == null) throw new IllegalArgumentException(imagePath + " is not a readable PNG image");

            String textureName = "web_" + itemName.replace('-', '_');
            KciTexture texture = KciTexture.createQuick(textureName, image);
            itemSet.textures.add(texture);

                    KciTool item = createItem(itemType);
            item.setName(itemName);
            item.setDisplayName(requireString(itemJson, "displayName", itemFile));
            item.setLore(readLore(itemJson));
            List<LeveledEnchantment> enchantments = readEnchantments(itemJson, itemFile);
            item.setDefaultEnchantments(enchantments);
            item.setAllowEnchanting(enchantments.isEmpty());
            item.setAttributeModifiers(readAttributes(itemJson, itemFile));
            item.setTexture(itemSet.textures.getReference(textureName));
            item.setUpdateAutomatically(false);
            item.setItemDamage((short) requireInteger(itemJson, "customModelData", itemFile));
            if (item instanceof KciArmor) {
                ArmorTexture armorTexture = loadArmorTexture(itemJson, itemFile, texturesDirectory, armorTextures, itemSet);
                if (armorTexture != null) ((KciArmor) item).setArmorTexture(itemSet.armorTextures.getReference(armorTexture.getName()));
            }
            itemSet.items.add(item);
        }

        itemSet.validateExportVersion(MCVersions.VERSION26);
        itemSet.assignInternalItemDamages();
        ByteArrayBitOutput output = new ByteArrayBitOutput();
        itemSet.save(output, ItemSet.Side.PLUGIN);
        output.terminate();
        Files.createDirectories(outputFile.getParent());
        byte[] cisText = StringEncoder.encodeTextyBytes(output.getBytes(), true);
        Files.write(outputFile, cisText);
        Files.createDirectories(resourcePackFile.getParent());
        try (OutputStream resourcePackOutput = Files.newOutputStream(resourcePackFile)) {
            new ResourcepackGenerator(itemSet).write(resourcePackOutput, cisText, null, true);
        }
        System.out.println("Wrote " + outputFile + " and " + resourcePackFile);
    }

    private static String requireString(JsonObject json, String key, Path source) {
        Object value = json.get(key);
        if (!(value instanceof String) || ((String) value).isBlank()) {
            throw new IllegalArgumentException(source + " requires non-empty string " + key);
        }
        return (String) value;
    }

    private static int requireInteger(JsonObject json, String key, Path source) {
        Object value = json.get(key);
        if (!(value instanceof Number)) throw new IllegalArgumentException(source + " requires numeric " + key);
        int result = ((Number) value).intValue();
        if (result < 1 || result > Short.MAX_VALUE) {
            throw new IllegalArgumentException(source + " has " + key + " outside the supported range 1-32767");
        }
        return result;
    }

    private static KciItemType parseItemType(String material, Path source) {
        try {
            KciItemType type = KciItemType.valueOf(material);
            createItem(type);
            return type;
        } catch (IllegalArgumentException unknownMaterial) {
            throw new IllegalArgumentException(source + " has invalid material " + material, unknownMaterial);
        }
    }

    private static KciTool createItem(KciItemType type) {
        if (type.getMainCategory() == KciItemType.Category.HELMET || type.getMainCategory() == KciItemType.Category.CHESTPLATE
                || type.getMainCategory() == KciItemType.Category.LEGGINGS || type.getMainCategory() == KciItemType.Category.BOOTS) {
            return new KciArmor(true, type);
        }
        if (type.getMainCategory() == KciItemType.Category.HOE) {
            KciHoe hoe = new KciHoe(true);
            hoe.setItemType(type);
            return hoe;
        }
        if (type == KciItemType.SHEARS) return new KciShears(true);
        if (type.getMainCategory() == KciItemType.Category.SWORD || type.getMainCategory() == KciItemType.Category.PICKAXE
                || type.getMainCategory() == KciItemType.Category.AXE || type.getMainCategory() == KciItemType.Category.SHOVEL
                || type.getMainCategory() == KciItemType.Category.FISHING || type.getMainCategory() == KciItemType.Category.FLINT
                || type.getMainCategory() == KciItemType.Category.CARROTSTICK) return new KciTool(true, type);
        throw new IllegalArgumentException("Unsupported material " + type + ". This export currently supports standard tools and armor.");
    }

    private static List<String> readLore(JsonObject json) {
        Object lore = json.get("lore");
        if (!(lore instanceof List<?>)) return List.of();
        List<String> result = new ArrayList<>();
        for (Object line : (List<?>) lore) {
            if (!(line instanceof String)) throw new IllegalArgumentException("Lore lines must be strings");
            result.add((String) line);
        }
        return result;
    }

    private static List<LeveledEnchantment> readEnchantments(JsonObject json, Path source) {
        Object enchantments = json.get("enchantments");
        if (!(enchantments instanceof List<?>)) return List.of();
        List<LeveledEnchantment> result = new ArrayList<>();
        for (Object entry : (List<?>) enchantments) {
            if (!(entry instanceof JsonObject)) throw new IllegalArgumentException(source + " has invalid enchantment entry");
            JsonObject enchantment = (JsonObject) entry;
            try {
                result.add(LeveledEnchantment.createQuick(VEnchantmentType.valueOf(requireString(enchantment, "type", source)), requireInteger(enchantment, "level", source)));
            } catch (IllegalArgumentException unknownType) {
                throw new IllegalArgumentException(source + " has invalid enchantment type", unknownType);
            }
        }
        return result;
    }

    private static List<KciAttributeModifier> readAttributes(JsonObject json, Path source) {
        Object attributes = json.get("attributes");
        if (!(attributes instanceof List<?>)) return List.of();
        List<KciAttributeModifier> result = new ArrayList<>();
        for (Object entry : (List<?>) attributes) {
            if (!(entry instanceof JsonObject)) throw new IllegalArgumentException(source + " has invalid attribute entry");
            JsonObject attribute = (JsonObject) entry;
            Object value = attribute.get("value");
            if (!(value instanceof Number) || !Double.isFinite(((Number) value).doubleValue())) throw new IllegalArgumentException(source + " attribute value must be finite");
            try {
                result.add(KciAttributeModifier.createQuick(
                        KciAttributeModifier.Attribute.valueOf(requireString(attribute, "attribute", source)),
                        KciAttributeModifier.Slot.valueOf(requireString(attribute, "slot", source)),
                        KciAttributeModifier.Operation.valueOf(requireString(attribute, "operation", source)),
                        ((Number) value).doubleValue()
                ));
            } catch (IllegalArgumentException unknownValue) {
                throw new IllegalArgumentException(source + " has invalid attribute type, slot, or operation", unknownValue);
            }
        }
        return result;
    }

    private static ArmorTexture loadArmorTexture(
            JsonObject itemJson, Path source, Path texturesDirectory, Map<String, ArmorTexture> loadedTextures, ItemSet itemSet
    ) throws Exception {
        Object value = itemJson.get("armorTexture");
        if (value == null) return null;
        if (!(value instanceof JsonObject)) throw new IllegalArgumentException(source + " has invalid armor texture settings");
        JsonObject settings = (JsonObject) value;
        String name = requireString(settings, "name", source);
        ArmorTexture existing = loadedTextures.get(name);
        if (existing != null) return existing;
        BufferedImage layer1 = readArmorLayer(texturesDirectory, requireString(settings, "layer1", source), source);
        BufferedImage layer2 = readArmorLayer(texturesDirectory, requireString(settings, "layer2", source), source);
        ArmorTexture armorTexture = new ArmorTexture(true);
        armorTexture.setName(name);
        armorTexture.setLayer1(layer1);
        armorTexture.setLayer2(layer2);
        itemSet.armorTextures.add(armorTexture);
        loadedTextures.put(name, armorTexture);
        return armorTexture;
    }

    private static BufferedImage readArmorLayer(Path texturesDirectory, String relativePath, Path source) throws Exception {
        Path path = texturesDirectory.resolve(relativePath).normalize();
        if (!path.startsWith(texturesDirectory) || !Files.isRegularFile(path)) throw new IllegalArgumentException(source + " refers to missing armor texture " + relativePath);
        BufferedImage image = ImageIO.read(path.toFile());
        if (image == null) throw new IllegalArgumentException(path + " is not a readable PNG image");
        return image;
    }
}