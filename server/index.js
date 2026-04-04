const express = require('express');
const cors = require('cors');
const crypto = require('crypto');
const { initDatabase } = require('./db');

const app = express();
const PORT = process.env.PORT || 3847;

app.use(cors());
app.use(express.json());
app.use(express.static('public', {
  etag: false,
  maxAge: 0,
  setHeaders: (res) => {
    res.set('Cache-Control', 'no-store, no-cache, must-revalidate');
    res.set('Pragma', 'no-cache');
  }
}));

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
 * GET /api/locations/clustered
 * Returns spatially-clustered task locations with weighted centroids.
 * 
 * Algorithm:
 * 1. Group all completions by struct_id
 * 2. For each task, cluster nearby points (within CLUSTER_RADIUS tiles)
 * 3. Compute weighted centroid for each cluster (weighted by hits)
 * 4. Filter: only return clusters with >= MIN_CLUSTER_PCT of total task hits
 * 
 * Query params:
 *   ?radius=10      Cluster radius in tiles (default 10)
 *   ?threshold=0.10 Minimum % of total task hits (default 0.10 = 10%)
 */
app.get('/api/locations/clustered', (req, res) => {
  try {
    const CLUSTER_RADIUS = parseInt(req.query.radius) || 10;
    const MIN_CLUSTER_PCT = parseFloat(req.query.threshold) || 0.10;

    // Get all raw locations grouped by task
    const allLocations = db.prepare(`
      SELECT task_name, struct_id, x, y, plane, hits
      FROM task_completions
      ORDER BY struct_id, hits DESC
    `).all();

    // Group by struct_id
    const byTask = {};
    for (const loc of allLocations) {
      if (!byTask[loc.struct_id]) {
        byTask[loc.struct_id] = { task_name: loc.task_name, locations: [] };
      }
      byTask[loc.struct_id].locations.push(loc);
    }

    const results = [];

    for (const [structId, task] of Object.entries(byTask)) {
      const locations = task.locations;
      const totalHits = locations.reduce((sum, l) => sum + l.hits, 0);

      // Greedy clustering: assign each point to nearest existing cluster or create new
      const clusters = [];
      for (const loc of locations) {
        let assigned = false;
        for (const cluster of clusters) {
          // Check if this point is within radius of the cluster centroid
          const dx = Math.abs(loc.x - cluster.centroidX);
          const dy = Math.abs(loc.y - cluster.centroidY);
          if (dx <= CLUSTER_RADIUS && dy <= CLUSTER_RADIUS) {
            // Add to cluster, recompute weighted centroid
            const oldWeight = cluster.hits;
            const newWeight = oldWeight + loc.hits;
            cluster.centroidX = Math.round((cluster.centroidX * oldWeight + loc.x * loc.hits) / newWeight);
            cluster.centroidY = Math.round((cluster.centroidY * oldWeight + loc.y * loc.hits) / newWeight);
            cluster.hits = newWeight;
            cluster.points.push(loc);
            assigned = true;
            break;
          }
        }
        if (!assigned) {
          clusters.push({
            centroidX: loc.x,
            centroidY: loc.y,
            hits: loc.hits,
            plane: loc.plane,
            points: [loc]
          });
        }
      }

      // Apply threshold filter and build results
      for (const cluster of clusters) {
        const pct = cluster.hits / totalHits;
        if (pct >= MIN_CLUSTER_PCT) {
          results.push({
            task_name: task.task_name,
            struct_id: parseInt(structId),
            x: cluster.centroidX,
            y: cluster.centroidY,
            plane: cluster.plane,
            hits: cluster.hits,
            total_hits: totalHits,
            percentage: Math.round(pct * 100),
            point_count: cluster.points.length
          });
        }
      }
    }

    // Sort by struct_id, then by hits descending
    results.sort((a, b) => a.struct_id - b.struct_id || b.hits - a.hits);

    res.json(results);
  } catch (err) {
    console.error('GET /api/locations/clustered error:', err);
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
 * GET /api/submissions
 * Returns all IP submissions (for admin dashboard).
 */
app.get('/api/submissions', (req, res) => {
  try {
    const rows = db.prepare(`
      SELECT id, ip_hash, struct_id, x, y, plane, submitted_at
      FROM ip_submissions
      ORDER BY submitted_at DESC
      LIMIT 1000
    `).all();
    res.json(rows);
  } catch (err) {
    console.error('GET /api/submissions error:', err);
    res.status(500).json({ error: 'Internal server error' });
  }
});

/**
 * DELETE /api/submissions/:id
 * Deletes a specific submission and decrements the corresponding location hits.
 */
app.delete('/api/submissions/:id', (req, res) => {
  try {
    const id = parseInt(req.params.id, 10);
    if (isNaN(id)) {
      return res.status(400).json({ error: 'Invalid submission ID' });
    }

    // Get the submission details before deleting
    const submission = db.prepare(
      'SELECT struct_id, x, y, plane FROM ip_submissions WHERE id = ?'
    ).get(id);

    if (!submission) {
      return res.status(404).json({ error: 'Submission not found' });
    }

    // Delete the submission
    db.prepare('DELETE FROM ip_submissions WHERE id = ?').run(id);

    // Decrement hits for the corresponding location
    db.prepare(`
      UPDATE task_completions
      SET hits = hits - 1
      WHERE struct_id = ? AND x = ? AND y = ? AND plane = ?
    `).run(submission.struct_id, submission.x, submission.y, submission.plane);

    // Clean up zero-hit locations
    db.prepare('DELETE FROM task_completions WHERE hits <= 0').run();

    res.json({ success: true, message: 'Submission deleted' });
  } catch (err) {
    console.error('DELETE /api/submissions/:id error:', err);
    res.status(500).json({ error: 'Internal server error' });
  }
});

/**
 * GET /api/blacklist
 * Returns all blacklisted IPs (for admin dashboard).
 */
app.get('/api/blacklist', (req, res) => {
  try {
    const rows = db.prepare(`
      SELECT ip_hash, reason, blacklisted_at
      FROM ip_blacklist
      ORDER BY blacklisted_at DESC
    `).all();
    res.json(rows);
  } catch (err) {
    console.error('GET /api/blacklist error:', err);
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
  console.log(`  GET  /api/locations/clustered - Clustered locations (heatmap)`);
  console.log(`  GET  /api/stats            - Server statistics`);
  console.log(`  POST /api/admin/blacklist  - Blacklist IP (admin)`);
});
