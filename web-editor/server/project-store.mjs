import { mkdir, readFile, readdir, rename, rm, writeFile } from 'node:fs/promises';
import path from 'node:path';

const previousMaterials = ['DIAMOND_SWORD', 'IRON_SWORD', 'NETHERITE_SWORD', 'LEATHER_HELMET', 'LEATHER_CHESTPLATE', 'LEATHER_LEGGINGS', 'LEATHER_BOOTS', 'CHAINMAIL_HELMET', 'CHAINMAIL_CHESTPLATE', 'CHAINMAIL_LEGGINGS', 'CHAINMAIL_BOOTS', 'IRON_HELMET', 'IRON_CHESTPLATE', 'IRON_LEGGINGS', 'IRON_BOOTS', 'DIAMOND_HELMET', 'DIAMOND_CHESTPLATE', 'DIAMOND_LEGGINGS', 'DIAMOND_BOOTS', 'NETHERITE_HELMET', 'NETHERITE_CHESTPLATE', 'NETHERITE_LEGGINGS', 'NETHERITE_BOOTS'];
const standardMaterials = ['WOOD_SWORD', 'STONE_SWORD', 'IRON_SWORD', 'DIAMOND_SWORD', 'GOLD_SWORD', 'NETHERITE_SWORD', 'WOOD_PICKAXE', 'STONE_PICKAXE', 'IRON_PICKAXE', 'DIAMOND_PICKAXE', 'GOLD_PICKAXE', 'NETHERITE_PICKAXE', 'WOOD_AXE', 'STONE_AXE', 'IRON_AXE', 'DIAMOND_AXE', 'GOLD_AXE', 'NETHERITE_AXE', 'WOOD_SHOVEL', 'STONE_SHOVEL', 'IRON_SHOVEL', 'DIAMOND_SHOVEL', 'GOLD_SHOVEL', 'NETHERITE_SHOVEL', 'WOOD_HOE', 'STONE_HOE', 'IRON_HOE', 'DIAMOND_HOE', 'GOLD_HOE', 'NETHERITE_HOE', 'SHEARS', 'FISHING_ROD', 'FLINT_AND_STEEL', 'CARROT_STICK', 'BOW', 'CROSSBOW', 'SHIELD'];
const armorMaterials = ['LEATHER_HELMET', 'LEATHER_CHESTPLATE', 'LEATHER_LEGGINGS', 'LEATHER_BOOTS', 'CHAINMAIL_HELMET', 'CHAINMAIL_CHESTPLATE', 'CHAINMAIL_LEGGINGS', 'CHAINMAIL_BOOTS', 'IRON_HELMET', 'IRON_CHESTPLATE', 'IRON_LEGGINGS', 'IRON_BOOTS', 'DIAMOND_HELMET', 'DIAMOND_CHESTPLATE', 'DIAMOND_LEGGINGS', 'DIAMOND_BOOTS', 'GOLD_HELMET', 'GOLD_CHESTPLATE', 'GOLD_LEGGINGS', 'GOLD_BOOTS', 'NETHERITE_HELMET', 'NETHERITE_CHESTPLATE', 'NETHERITE_LEGGINGS', 'NETHERITE_BOOTS'];

const defaultConfig = {
  host: '0.0.0.0',
  port: 3210,
  paths: { items: 'items', textures: 'textures', export: 'export' },
  validation: {
    allowedMaterials: [...standardMaterials, ...armorMaterials],
    customModelData: { minimum: 1, maximum: 32767 }
  }
};

export function defaultItem() {
  return { id: '', displayName: '', material: 'DIAMOND_SWORD', customModelData: 1, texture: '', lore: [], enchantments: [], attributes: [] };
}

export class ProjectStore {
  constructor(projectDirectory) {
    this.projectDirectory = path.resolve(projectDirectory);
    this.configPath = path.join(this.projectDirectory, 'kci-editor.config.json');
  }

  async initialize() {
    await mkdir(this.projectDirectory, { recursive: true });
    try {
      this.config = { ...defaultConfig, ...JSON.parse(await readFile(this.configPath, 'utf8')) };
      if (JSON.stringify(this.config.validation.allowedMaterials) === JSON.stringify(previousMaterials) || !this.config.validation.allowedMaterials.includes('BOW')) {
        this.config.validation.allowedMaterials = [...defaultConfig.validation.allowedMaterials];
        this.config.validation.customModelData = { ...defaultConfig.validation.customModelData };
        await writeFile(this.configPath, `${JSON.stringify(this.config, null, 2)}\n`);
      }
    } catch (error) {
      if (error.code !== 'ENOENT') throw error;
      this.config = structuredClone(defaultConfig);
      await writeFile(this.configPath, `${JSON.stringify(this.config, null, 2)}\n`);
    }
    for (const relativePath of Object.values(this.config.paths)) await mkdir(this.resolve(relativePath), { recursive: true });
  }

  resolve(relativePath) {
    const resolved = path.resolve(this.projectDirectory, relativePath);
    if (!resolved.startsWith(`${this.projectDirectory}${path.sep}`) && resolved !== this.projectDirectory) throw new Error('Invalid project path');
    return resolved;
  }

  itemPath(id) {
    if (!/^[a-z0-9][a-z0-9_-]{0,63}$/.test(id)) throw new Error('Item IDs use lowercase letters, numbers, hyphens, and underscores.');
    return this.resolve(path.join(this.config.paths.items, `${id}.json`));
  }

  async listItems() {
    const directory = this.resolve(this.config.paths.items);
    const names = await readdir(directory);
    const items = await Promise.all(names.filter(name => name.endsWith('.json')).map(async name => JSON.parse(await readFile(path.join(directory, name), 'utf8'))));
    return items.sort((left, right) => left.id.localeCompare(right.id));
  }

  async saveItem(previousId, item) {
    const errors = await this.validate(item, previousId);
    if (errors.some(problem => problem.level === 'error')) return { problems: errors };
    const destination = this.itemPath(item.id);
    const temporary = `${destination}.tmp`;
    await writeFile(temporary, `${JSON.stringify(item, null, 2)}\n`);
    await rename(temporary, destination);
    if (previousId && previousId !== item.id) await rm(this.itemPath(previousId), { force: true });
    return { item, problems: errors };
  }

  async deleteItem(id) {
    await rm(this.itemPath(id));
  }

  async listTextures() {
    const directory = this.resolve(this.config.paths.textures);
    const entries = await readdir(directory, { recursive: true });
    return entries.filter(entry => entry.toLowerCase().endsWith('.png')).sort();
  }

  async exportSnapshot() {
    const items = await this.listItems();
    const destination = this.resolve(path.join(this.config.paths.export, 'items.json'));
    await writeFile(destination, `${JSON.stringify({ format: 'kci-web-editor-project-v1', items }, null, 2)}\n`);
    return destination;
  }

  async validate(item, previousId) {
    const rules = this.config.validation;
    const problems = [];
    if (!/^[a-z0-9][a-z0-9_-]{0,63}$/.test(item.id || '')) problems.push({ level: 'error', field: 'id', message: 'Use 1-64 lowercase letters, numbers, hyphens, or underscores.' });
    if (!item.displayName?.trim()) problems.push({ level: 'error', field: 'displayName', message: 'A display name is required.' });
    if (!rules.allowedMaterials.includes(item.material)) problems.push({ level: 'error', field: 'material', message: 'The selected base material is not allowed by this project.' });
    const { minimum, maximum } = rules.customModelData;
    if (!Number.isInteger(item.customModelData) || item.customModelData < minimum || item.customModelData > maximum) problems.push({ level: 'error', field: 'customModelData', message: `Use an integer from ${minimum} to ${maximum}.` });
    const conflictingItem = (await this.listItems()).find(candidate => candidate.id !== previousId && candidate.material === item.material && candidate.customModelData === item.customModelData);
    if (conflictingItem) problems.push({ level: 'error', field: 'customModelData', message: `${item.material} model data ${item.customModelData} is already used by ${conflictingItem.id}. Choose a different number.` });
    if (!Array.isArray(item.enchantments) || item.enchantments.some(enchantment => !enchantment.type || !Number.isInteger(enchantment.level) || enchantment.level < 1)) problems.push({ level: 'error', field: 'enchantments', message: 'Every enchantment needs a type and positive integer level.' });
    if (!Array.isArray(item.attributes) || item.attributes.some(attribute => !attribute.attribute || !attribute.slot || !attribute.operation || !Number.isFinite(attribute.value))) problems.push({ level: 'error', field: 'attributes', message: 'Every attribute needs a type, slot, operation, and finite value.' });
    if (item.material?.match(/_(HELMET|CHESTPLATE|LEGGINGS|BOOTS)$/) && item.armorTexture && (!item.armorTexture.name || !item.armorTexture.layer1 || !item.armorTexture.layer2)) problems.push({ level: 'error', field: 'armorTexture', message: 'A worn armor texture set needs a name, layer 1 PNG, and layer 2 PNG.' });
    if (!item.texture) problems.push({ level: 'warning', field: 'texture', message: 'No texture is assigned.' });
    return problems;
  }
}