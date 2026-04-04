const express = require('express');
const cors = require('cors');
const crypto = require('crypto');
const { initDatabase } = require('./db');

const app = express();
const PORT = process.env.PORT || 3847;

app.use(cors());
app.use(express.json());

const db = initDatabase();

// Prepared statements for performance
const stmts = {
  // Upsert: insert new location or increment hits
  upsertCompletion: db.prepare(`
    INSERT INTO task_completions (task_name, struct_id, x, y, plane, hits)
    VALUES (?, ?, ?, ?, ?, 1)
    ON CONFLICT(struct_id, x, y, plane)
    DO UPDATE SET hits = hits + 1, last_seen = datetime('now')
  `),

  // Track IP submission
  insertIpSubmission: db.prepare(`
    INSERT INTO ip_submissions (ip_hash, struct_id, x, y, plane)
    VALUES (?, ?, ?, ?, ?)
  `),

  // Check if IP is blacklisted
  checkBlacklist: db.prepare(`
    SELECT 1 FROM ip_blacklist WHERE ip_hash = ?
  `),

  // Get all locations (aggregated)
  getAllLocations: db.prepare(`
    SELECT task_name, struct_id, x, y, plane, hits, first_seen, last_seen
    FROM task_completions
    ORDER BY struct_id, hits DESC
  `),

  // Get locations for a specific task
  getLocationsByTask: db.prepare(`
    SELECT task_name, struct_id, x, y, plane, hits, first_seen, last_seen
    FROM task_completions
    WHERE struct_id = ?
    ORDER BY hits DESC
  `),

  // Revoke all contributions from a blacklisted IP
  getSubmissionsByIp: db.prepare(`
    SELECT struct_id, x, y, plane FROM ip_submissions WHERE ip_hash = ?
  `),

  decrementHits: db.prepare(`
    UPDATE task_completions
    SET hits = hits - 1
    WHERE struct_id = ? AND x = ? AND y = ? AND plane = ?
  `),

  cleanupZeroHits: db.prepare(`
    DELETE FROM task_completions WHERE hits <= 0
  `)
};

// Hash an IP for storage (we don't store raw IPs)
function hashIp(ip) {
  return crypto.createHash('sha256').update(ip || 'unknown').digest('hex').substring(0, 16);
}

// Get client IP (supports X-Forwarded-For for Nginx/Cloudflare)
function getClientIp(req) {
  return req.headers['x-forwarded-for']?.split(',')[0]?.trim() || req.ip || 'unknown';
}

/**
 * POST /api/submit
 * Body: { task_name, struct_id, x, y, plane }
 *
 * Submits a task completion location. Increments hits if location already known.
 * Tracks IP for audit purposes.
 */
app.post('/api/submit', (req, res) => {
  try {
    const { task_name, struct_id, x, y, plane } = req.body;

    // Validate payload
    if (!task_name || struct_id == null || x == null || y == null) {
      return res.status(400).json({ error: 'Missing required fields: task_name, struct_id, x, y' });
    }

    const ipHash = hashIp(getClientIp(req));
    const taskPlane = plane || 0;

    // Check blacklist
    if (stmts.checkBlacklist.get(ipHash)) {
      return res.status(403).json({ error: 'Submissions blocked' });
    }

    // Upsert the completion location
    stmts.upsertCompletion.run(task_name, struct_id, x, y, taskPlane);

    // Track the IP submission
    stmts.insertIpSubmission.run(ipHash, struct_id, x, y, taskPlane);

    res.json({ success: true, message: 'Submission recorded' });
  } catch (err) {
    console.error('POST /api/submit error:', err);
    res.status(500).json({ error: 'Internal server error' });
  }
});

/**
 * GET /api/locations
 * Returns all crowdsourced task locations, aggregated by hits.
 */
app.get('/api/locations', (req, res) => {
  try {
    const rows = stmts.getAllLocations.all();
    res.json(rows);
  } catch (err) {
    console.error('GET /api/locations error:', err);
    res.status(500).json({ error: 'Internal server error' });
  }
});

/**
 * GET /api/locations/:structId
 * Returns locations for a specific task.
 */
app.get('/api/locations/:structId', (req, res) => {
  try {
    const structId = parseInt(req.params.structId, 10);
    if (isNaN(structId)) {
      return res.status(400).json({ error: 'Invalid structId' });
    }
    const rows = stmts.getLocationsByTask.all(structId);
    res.json(rows);
  } catch (err) {
    console.error('GET /api/locations/:structId error:', err);
    res.status(500).json({ error: 'Internal server error' });
  }
});

/**
 * POST /api/admin/blacklist
 * Body: { ip_hash, reason }
 *
 * Blacklists an IP and revokes all their contributions.
 * TODO: Add admin auth before deploying to production.
 */
app.post('/api/admin/blacklist', (req, res) => {
  try {
    const { ip_hash, reason } = req.body;
    if (!ip_hash) {
      return res.status(400).json({ error: 'Missing ip_hash' });
    }

    // Add to blacklist
    db.prepare(`
      INSERT OR IGNORE INTO ip_blacklist (ip_hash, reason)
      VALUES (?, ?)
    `).run(ip_hash, reason || 'No reason provided');

    // Revoke all their submissions
    const submissions = stmts.getSubmissionsByIp.all(ip_hash);
    for (const sub of submissions) {
      stmts.decrementHits.run(sub.struct_id, sub.x, sub.y, sub.plane);
    }
    stmts.cleanupZeroHits.run();

    res.json({ success: true, revoked: submissions.length });
  } catch (err) {
    console.error('POST /api/admin/blacklist error:', err);
    res.status(500).json({ error: 'Internal server error' });
  }
});

/**
 * GET /api/stats
 * Basic server stats for monitoring.
 */
app.get('/api/stats', (req, res) => {
  try {
    const totalLocations = db.prepare('SELECT COUNT(*) as count FROM task_completions').get().count;
    const totalSubmissions = db.prepare('SELECT COUNT(*) as count FROM ip_submissions').get().count;
    const uniqueTasks = db.prepare('SELECT COUNT(DISTINCT struct_id) as count FROM task_completions').get().count;
    const blacklistedIps = db.prepare('SELECT COUNT(*) as count FROM ip_blacklist').get().count;
    res.json({ totalLocations, totalSubmissions, uniqueTasks, blacklistedIps });
  } catch (err) {
    console.error('GET /api/stats error:', err);
    res.status(500).json({ error: 'Internal server error' });
  }
});

app.listen(PORT, () => {
  console.log(`UTM Crowdsource Server running on port ${PORT}`);
  console.log(`  POST /api/submit          - Submit task completion`);
  console.log(`  GET  /api/locations        - Get all locations`);
  console.log(`  GET  /api/locations/:id    - Get locations for task`);
  console.log(`  GET  /api/stats            - Server statistics`);
  console.log(`  POST /api/admin/blacklist  - Blacklist IP (admin)`);
});
