/**
 * StageMobile - SoundFont Manager (Admin SDK)
 * Uso: node manage-soundfonts.js [list | add | delete]
 */

const admin = require('firebase-admin');
const path = require('path');
const fs = require('fs');

// Path para a chave que você enviou
const serviceAccount = require('./stagemobileapp-31ad1-firebase-adminsdk-fbsvc-32464a128d.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();
const collection = db.collection('soundfonts');

async function listSoundFonts() {
  console.log('\n--- Biblioteca de SoundFonts (Firestore) ---');
  const snapshot = await collection.orderBy('fileName').get();
  if (snapshot.empty) {
    console.log('Nenhum registro encontrado.');
    return;
  }

  snapshot.forEach(doc => {
    const data = doc.data();
    console.log(`- ${data.fileName} [ID: ${doc.id}]`);
    console.log(`  Tags: ${data.tags.join(', ')}`);
    console.log(`  Sistema: ${data.isSystem ? 'Sim' : 'Não'}`);
    console.log('-------------------------------------------');
  });
}

async function addSoundFont(fileName, tagsString) {
  const tags = tagsString.split(',').map(t => t.trim());
  const id = fileName.replace(/\.[^/.]+$/, "").toLowerCase(); // Usa nome sem extensão como ID

  await collection.doc(id).set({
    id: id,
    fileName: fileName,
    tags: tags,
    isSystem: false,
    addedDate: admin.firestore.FieldValue.serverTimestamp()
  }, { merge: true });

  console.log(`✅ Metadados de '${fileName}' salvos/atualizados com sucesso!`);
}

async function deleteSoundFont(fileName) {
    const id = fileName.replace(/\.[^/.]+$/, "").toLowerCase();
    await collection.doc(id).delete();
    console.log(`🗑️ Registro de '${fileName}' removido do Firestore.`);
}

const args = process.argv.slice(2);
const command = args[0];

(async () => {
  try {
    switch (command) {
      case 'list':
        await listSoundFonts();
        break;
      case 'add':
        if (!args[1] || !args[2]) {
          console.log('Uso: node manage-soundfonts.js add "arquivo.sf2" "Tag1, Tag2"');
        } else {
          await addSoundFont(args[1], args[2]);
        }
        break;
      case 'delete':
        if (!args[1]) {
          console.log('Uso: node manage-soundfonts.js delete "arquivo.sf2"');
        } else {
          await deleteSoundFont(args[1]);
        }
        break;
      default:
        console.log('Comandos disponíveis: list, add, delete');
    }
  } catch (error) {
    console.error('❌ Erro:', error.message);
  } finally {
    process.exit();
  }
})();
