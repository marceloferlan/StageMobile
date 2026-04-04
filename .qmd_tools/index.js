import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { program } from 'commander';
import MiniSearch from 'minisearch';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const INDEX_FILE = path.join(__dirname, 'mini-qmd-index.json');

// Função recursiva para encontrar arquivos .md
function findMarkdownFiles(dir, fileList = []) {
  if (!fs.existsSync(dir)) return fileList;
  const files = fs.readdirSync(dir);
  for (const file of files) {
    const fullPath = path.join(dir, file);
    if (fs.statSync(fullPath).isDirectory()) {
      findMarkdownFiles(fullPath, fileList);
    } else if (fullPath.endsWith('.md')) {
      fileList.push(fullPath);
    }
  }
  return fileList;
}

// Lógica de Smart Chunking (fragmenta por cabeçalhos ## ou quebras duplas)
function chunkDocument(filePath, content) {
  const chunks = [];
  const sections = content.split(/\n#{1,3}\s/);
  
  sections.forEach((section, index) => {
    if (!section.trim()) return;
    const text = (index > 0 ? '# ' : '') + section.trim();
    if (text.length > 50) {
      chunks.push({
        id: `${filePath}#chunk${index}`,
        file: filePath,
        content: text.substring(0, 2000) // um pouco maior que o original
      });
    }
  });
  return chunks;
}

program
  .command('index <directories...>')
  .action((directories) => {
    let allChunks = [];
    directories.forEach(dir => {
      const fullDir = path.resolve(dir);
      console.log(`Indexando: ${fullDir}`);
      const mdFiles = findMarkdownFiles(fullDir);
      
      mdFiles.forEach(file => {
        const content = fs.readFileSync(file, 'utf-8');
        const chunks = chunkDocument(file, content);
        allChunks = allChunks.concat(chunks);
      });
    });

    const miniSearch = new MiniSearch({
      fields: ['content', 'file'],
      storeFields: ['content', 'file'],
      searchOptions: {
        boost: { file: 2 },
        fuzzy: 0.2
      }
    });

    miniSearch.addAll(allChunks);
    fs.writeFileSync(INDEX_FILE, JSON.stringify(miniSearch));
    console.log(`OK! ${allChunks.length} trechos indexados em ${INDEX_FILE}`);
  });

program
  .command('search <query...>')
  .option('-l, --limit <number>', 'Limite de trechos', 5)
  .action((queryArray, options) => {
    if (!fs.existsSync(INDEX_FILE)) {
      console.error("Erro: Execute o index 1o.");
      process.exit(1);
    }

    const query = queryArray.join(' ');
    const indexData = fs.readFileSync(INDEX_FILE, 'utf-8');
    const miniSearch = MiniSearch.loadJSON(indexData, {
      fields: ['content', 'file'],
      storeFields: ['content', 'file']
    });

    const results = miniSearch.search(query, { prefix: true, fuzzy: 0.2 });
    const topResults = results.slice(0, parseInt(options.limit));

    if (topResults.length === 0) {
      console.log(`Nada para: "${query}"`);
      return;
    }

    console.log(`\n🔎 [QMD] "${query}"\n`);
    topResults.forEach((res, i) => {
      console.log(`[${i + 1}] Score: ${res.score.toFixed(2)} - ${path.basename(res.file)}`);
      console.log(`${res.content.trim()}\n`);
    });
  });

program.parse();
