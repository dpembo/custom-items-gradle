import express from 'express';
import { existsSync } from 'node:fs';
import multer from 'multer';
import { spawn } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { ProjectStore } from './project-store.mjs';

const webRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const projectDirectory = process.env.KCI_PROJECT_DIR || path.join(webRoot, 'project');
const store = new ProjectStore(projectDirectory);
await store.initialize();

const app = express();
app.use(express.json({ limit: '1mb' }));
app.use('/textures', express.static(store.resolve(store.config.paths.textures)));
const textureDirectory = store.resolve(store.config.paths.textures);
const upload = multer({
	storage: multer.diskStorage({
		destination: textureDirectory,
		filename: (request, file, callback) => callback(null, `${Date.now()}-${file.originalname.toLowerCase().replace(/[^a-z0-9._-]/g, '-')}`)
	}),
	limits: { fileSize: 4 * 1024 * 1024 },
	fileFilter: (request, file, callback) => callback(null, file.mimetype === 'image/png' && file.originalname.toLowerCase().endsWith('.png'))
});
app.get('/api/project', (request, response) => response.json({ directory: store.projectDirectory, config: store.config }));
app.get('/api/items', async (request, response, next) => { try { response.json(await store.listItems()); } catch (error) { next(error); } });
app.get('/api/textures', async (request, response, next) => { try { response.json(await store.listTextures()); } catch (error) { next(error); } });
app.post('/api/textures', upload.single('texture'), (request, response) => {
	if (!request.file) return response.status(422).json({ message: 'Choose a PNG texture no larger than 4 MB.' });
	response.status(201).json({ path: request.file.filename });
});
app.post('/api/items', async (request, response, next) => { try { const result = await store.saveItem(null, request.body); response.status(result.item ? 201 : 422).json(result); } catch (error) { next(error); } });
app.put('/api/items/:id', async (request, response, next) => { try { const result = await store.saveItem(request.params.id, request.body); response.status(result.item ? 200 : 422).json(result); } catch (error) { next(error); } });
app.delete('/api/items/:id', async (request, response, next) => { try { await store.deleteItem(request.params.id); response.sendStatus(204); } catch (error) { next(error); } });
app.post('/api/export', async (request, response, next) => { try {
	const destination = store.resolve(path.join(store.config.paths.export, 'items.cis.txt'));
	const resourcePack = store.resolve(path.join(store.config.paths.export, 'resource-pack.zip'));
	const repositoryRoot = path.resolve(webRoot, '..');
	await run(repositoryRoot, './gradlew', [':kci-export-cli:installDist']);
	const executable = path.join(repositoryRoot, 'kci-export-cli', 'build', 'install', 'kci-export-cli', 'bin', 'kci-export-cli');
	await run(repositoryRoot, executable, [store.resolve(store.config.paths.items), store.resolve(store.config.paths.textures), destination, resourcePack]);
	response.json({ message: `Wrote ${destination} and ${resourcePack}.`, destination, resourcePack });
} catch (error) {
	response.status(500).json({ message: 'Export failed. Review the details and correct the project before trying again.', details: error.message });
} });
app.use((error, request, response, next) => response.status(400).json({ message: error.message }));

function run(cwd, command, args) {
	return new Promise((resolve, reject) => {
		const child = spawn(command, args, { cwd, stdio: 'pipe' });
		let output = '';
		child.stdout.on('data', chunk => { output += chunk; });
		child.stderr.on('data', chunk => { output += chunk; });
		child.on('error', reject);
		child.on('close', code => code === 0 ? resolve(output) : reject(new Error(output.trim() || `${command} exited with status ${code}`)));
	});
}

const clientDist = path.join(webRoot, 'dist');
if (existsSync(clientDist)) app.use(express.static(clientDist));
app.listen(store.config.port, store.config.host, () => console.log(`KCI Web Editor: http://${store.config.host}:${store.config.port}\nProject: ${store.projectDirectory}`));