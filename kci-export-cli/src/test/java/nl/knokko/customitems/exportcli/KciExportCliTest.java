package nl.knokko.customitems.exportcli;

import nl.knokko.customitems.bithelper.ByteArrayBitInput;
import nl.knokko.customitems.itemset.ItemSet;
import nl.knokko.customitems.util.StringEncoder;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KciExportCliTest {

    @Test
    void exportsTextFileThatPluginSideCanLoad() throws Exception {
        Path project = Files.createTempDirectory("kci-export-cli-test");
        Path itemsDirectory = Files.createDirectory(project.resolve("items"));
        Path texturesDirectory = Files.createDirectory(project.resolve("textures"));
        Path outputFile = project.resolve("export/items.cis.txt");
        Path resourcePackFile = project.resolve("export/resource-pack.zip");

        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        ImageIO.write(image, "PNG", texturesDirectory.resolve("frostbrand.png").toFile());
        BufferedImage armorLayer = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        ImageIO.write(armorLayer, "PNG", texturesDirectory.resolve("frost_armor_layer_1.png").toFile());
        ImageIO.write(armorLayer, "PNG", texturesDirectory.resolve("frost_armor_layer_2.png").toFile());
        Files.writeString(itemsDirectory.resolve("frostbrand.json"), """
                {"id":"frostbrand","displayName":"Frostbrand","material":"DIAMOND_SWORD","customModelData":1001,"texture":"frostbrand.png","lore":[],"enchantments":[{"type":"sharpness","level":5}],"attributes":[{"attribute":"ATTACK_DAMAGE","slot":"MAINHAND","operation":"ADD","value":7.5}]}
                """);
        Files.writeString(itemsDirectory.resolve("frosthelm.json"), """
            {"id":"frosthelm","displayName":"Frost Helm","material":"DIAMOND_HELMET","customModelData":1001,"texture":"frostbrand.png","lore":[],"enchantments":[],"attributes":[{"attribute":"ARMOR","slot":"HEAD","operation":"ADD","value":2}],"armorTexture":{"name":"frost_armor","layer1":"frost_armor_layer_1.png","layer2":"frost_armor_layer_2.png"}}
            """);
        Files.writeString(itemsDirectory.resolve("frosthoe.json"), """
            {"id":"frosthoe","displayName":"Frost Hoe","material":"NETHERITE_HOE","customModelData":1001,"texture":"frostbrand.png","lore":[],"enchantments":[],"attributes":[]}
            """);
        Files.writeString(itemsDirectory.resolve("frostshears.json"), """
            {"id":"frostshears","displayName":"Frost Shears","material":"SHEARS","customModelData":1001,"texture":"frostbrand.png","lore":[],"enchantments":[],"attributes":[]}
            """);
        Files.writeString(itemsDirectory.resolve("frostbow.json"), """
            {"id":"frostbow","displayName":"Frost Bow","material":"BOW","customModelData":1001,"texture":"frostbrand.png","lore":[],"enchantments":[],"attributes":[]}
            """);
        Files.writeString(itemsDirectory.resolve("frostcrossbow.json"), """
            {"id":"frostcrossbow","displayName":"Frost Crossbow","material":"CROSSBOW","customModelData":1001,"texture":"frostbrand.png","lore":[],"enchantments":[],"attributes":[]}
            """);
        Files.writeString(itemsDirectory.resolve("frostshield.json"), """
            {"id":"frostshield","displayName":"Frost Shield","material":"SHIELD","customModelData":1001,"texture":"frostbrand.png","lore":[],"enchantments":[],"attributes":[]}
            """);
        Files.writeString(itemsDirectory.resolve("frosttrident.json"), """
            {"id":"frosttrident","displayName":"Frost Trident","material":"TRIDENT","customModelData":1001,"texture":"frostbrand.png","lore":[],"enchantments":[],"attributes":[]}
            """);

        KciExportCli.main(new String[] { itemsDirectory.toString(), texturesDirectory.toString(), outputFile.toString(), resourcePackFile.toString() });

        assertTrue(Files.size(outputFile) > 0);
        ItemSet decoded = new ItemSet(
                new ByteArrayBitInput(StringEncoder.decodeTextyBytes(Files.readAllBytes(outputFile))),
                ItemSet.Side.PLUGIN, false
        );
        assertEquals(8, decoded.items.size());
        var frostbrand = decoded.items.get("frostbrand").orElseThrow();
        assertEquals("Frostbrand", frostbrand.getDisplayName());
        assertEquals(1, frostbrand.getDefaultEnchantments().size());
        assertEquals(1, frostbrand.getAttributeModifiers().size());
        try (ZipFile resourcePack = new ZipFile(resourcePackFile.toFile())) {
            assertTrue(resourcePack.getEntry("items.cis.txt") != null);
            assertTrue(resourcePack.getEntry("assets/minecraft/textures/customitems/web_frostbrand.png") != null);
            assertTrue(resourcePack.getEntry("assets/minecraft/models/customitems/frostbrand.json") != null);
            assertTrue(resourcePack.getEntry("assets/minecraft/equipment/kci_frost_armor.json") != null);
            assertTrue(resourcePack.getEntry("assets/minecraft/textures/entity/equipment/humanoid/kci_frost_armor.png") != null);
        }
    }
}