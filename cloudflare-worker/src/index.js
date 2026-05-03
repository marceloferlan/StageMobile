/**
 * StageMobile Backup Worker — Cloudflare R2 Storage Bridge
 *
 * Endpoints:
 *   PUT    /upload?key=path/to/file     — upload file (body = raw bytes)
 *   GET    /download?key=path/to/file   — download file
 *   DELETE /delete?key=path/to/file     — delete file
 *   GET    /list?prefix=path/to/dir/    — list files under prefix
 *
 * Auth: Authorization: Bearer <firebase-id-token>
 * R2 access via binding (no credentials in code — configured in wrangler.toml)
 */

export default {
  async fetch(request, env) {
    // ── CORS (preflight) ──────────────────────────────────────────
    if (request.method === 'OPTIONS') {
      return new Response(null, {
        headers: corsHeaders(),
      });
    }

    // ── Auth check ────────────────────────────────────────────────
    const authHeader = request.headers.get('Authorization');
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      return jsonResponse({ error: 'Unauthorized' }, 401);
    }

    // Extract userId from token (simple decode — production should verify signature)
    const token = authHeader.slice(7);
    const userId = extractUserIdFromToken(token);
    if (!userId) {
      return jsonResponse({ error: 'Invalid token' }, 401);
    }

    const url = new URL(request.url);
    const key = url.searchParams.get('key');
    const prefix = url.searchParams.get('prefix');

    // Validate that the key belongs to this user (prevent accessing other users' data)
    if (key && !key.startsWith(`backups/${userId}/`)) {
      return jsonResponse({ error: 'Forbidden: key must start with backups/{userId}/' }, 403);
    }
    if (prefix && !prefix.startsWith(`backups/${userId}/`)) {
      return jsonResponse({ error: 'Forbidden: prefix must start with backups/{userId}/' }, 403);
    }

    try {
      // ── UPLOAD ────────────────────────────────────────────────
      if (url.pathname === '/upload' && request.method === 'PUT') {
        if (!key) return jsonResponse({ error: 'Missing key parameter' }, 400);

        await env.BUCKET.put(key, request.body, {
          httpMetadata: {
            contentType: request.headers.get('Content-Type') || 'application/octet-stream',
          },
        });

        return jsonResponse({ success: true, key });
      }

      // ── DOWNLOAD ──────────────────────────────────────────────
      if (url.pathname === '/download' && request.method === 'GET') {
        if (!key) return jsonResponse({ error: 'Missing key parameter' }, 400);

        const object = await env.BUCKET.get(key);
        if (!object) return jsonResponse({ error: 'Not found' }, 404);

        return new Response(object.body, {
          headers: {
            ...corsHeaders(),
            'Content-Type': object.httpMetadata?.contentType || 'application/octet-stream',
            'Content-Length': object.size.toString(),
          },
        });
      }

      // ── DELETE ─────────────────────────────────────────────────
      if (url.pathname === '/delete' && request.method === 'DELETE') {
        if (!key) return jsonResponse({ error: 'Missing key parameter' }, 400);

        await env.BUCKET.delete(key);
        return jsonResponse({ success: true, key });
      }

      // ── LIST ───────────────────────────────────────────────────
      if (url.pathname === '/list' && request.method === 'GET') {
        if (!prefix) return jsonResponse({ error: 'Missing prefix parameter' }, 400);

        const listed = await env.BUCKET.list({ prefix, delimiter: '/' });
        const files = listed.objects.map((obj) => ({
          name: obj.key.replace(prefix, ''),
          size: obj.size,
          uploaded: obj.uploaded?.toISOString(),
        }));

        return jsonResponse({ files, truncated: listed.truncated });
      }

      return jsonResponse({ error: 'Unknown endpoint' }, 404);
    } catch (err) {
      return jsonResponse({ error: err.message || 'Internal Server Error' }, 500);
    }
  },
};

// ═══════════════════════════════════════════════════════════════════
// Helpers
// ═══════════════════════════════════════════════════════════════════

function corsHeaders() {
  return {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET, PUT, DELETE, OPTIONS',
    'Access-Control-Allow-Headers': 'Authorization, Content-Type',
  };
}

function jsonResponse(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      'Content-Type': 'application/json',
      ...corsHeaders(),
    },
  });
}

/**
 * Extrai o userId (sub claim) de um Firebase ID Token.
 * Decodifica o payload JWT sem verificar a assinatura.
 * PRODUÇÃO: deve verificar a assinatura contra as chaves públicas do Google.
 */
function extractUserIdFromToken(token) {
  try {
    const parts = token.split('.');
    if (parts.length !== 3) return null;
    const payload = JSON.parse(atob(parts[1].replace(/-/g, '+').replace(/_/g, '/')));
    return payload.sub || payload.user_id || null;
  } catch {
    return null;
  }
}
