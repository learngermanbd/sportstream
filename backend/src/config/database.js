/**
 * Phase 9 · Supabase database adapter.
 *
 * Replaces Prisma direct-connection with Supabase REST API (via service_role key).
 * Exposes the same API shape (findMany, findUnique, create, update, delete, count, etc.)
 * so all 12 controllers work without changes.
 *
 * Authenticates with the service_role key, which bypasses Row Level Security
 * and allows full CRUD on all tables.
 */

const { createClient } = require('@supabase/supabase-js');

let supabase = null;

/** Parse include to a Supabase select string (supports nested relations). */
function buildSelect(include) {
  if (!include) return '*';

  const parts = [];
  for (const [key, value] of Object.entries(include)) {
    if (key === '_count') {
      // _count is handled separately — skip it in select
      continue;
    }
    // Map relation name to Supabase table/relation name
    const relName = relationMap[key] || key;
    if (value === true) {
      parts.push(`${relName}(*)`);
    } else if (value && typeof value === 'object' && value.select) {
      const cols = Object.keys(value.select).join(', ');
      parts.push(`${relName}(${cols})`);
    }
  }
  return parts.length > 0 ? `*, ${parts.join(', ')}` : '*';
}

/** Map Prisma relation names to Supabase table names. */
const relationMap = {
  streams:    'StreamLink',
  category:   'Category',
  sentBy:     'Admin',
  channels:   'Channel',
  events:     'Event',
  notifications: 'Notification',
};

/** Map Prisma model names to Supabase table names (most are same, but some may differ). */
const TABLE_MAP = {
  event:           'Event',
  channel:         'Channel',
  category:        'Category',
  highlight:       'Highlight',
  banner:          'Banner',
  appConfig:       'AppConfig',
  notification:    'Notification',
  deviceToken:     'DeviceToken',
  analyticsEvent:  'AnalyticsEvent',
  admin:           'Admin',
  streamLink:      'StreamLink',
};

class SupabaseModel {
  constructor(sb, tableName) {
    this.sb = sb;
    this.tableName = tableName;
  }

  // ─── CRUD ───

  async findMany({ where, include, orderBy, skip, take, select: selectFields } = {}) {
    var hasCount = include && include._count;
    let selectStr = selectFields
      ? (Array.isArray(selectFields) ? selectFields.join(', ') : selectFields)
      : buildSelect(include);
    let query = this.sb.from(this.tableName).select(selectStr, { count: 'exact' });

    if (where) query = this._applyWhere(query, where);
    if (orderBy) query = this._applyOrder(query, orderBy);
    if (skip !== undefined && take !== undefined) {
      query = query.range(skip, skip + take - 1);
    }

    const { data, error } = await query;
    if (error) throw new Error(`[${this.tableName}/findMany] ${error.message}`);

    // Post-process _count includes (e.g. categories counting channels + events)
    // Uses batch queries (one per relation) instead of N+1 per-row queries.
    if (hasCount && data && data.length > 0) {
      var countSelect = hasCount.select || {};
      var fkMap = { channels: { fk: 'categoryId', tbl: 'Channel' }, events: { fk: 'categoryId', tbl: 'Event' } };
      var ids = data.map(function(r) { return r.id; });

      // Initialize _count to 0 for all rows
      for (var row of data) {
        row._count = {};
        for (var childName of Object.keys(countSelect)) { row._count[childName] = 0; }
      }

      // Batch-fetch counts: one query per relation for ALL matching rows
      for (var childName of Object.keys(countSelect)) {
        var fkInfo = fkMap[childName] || { fk: this.tableName.toLowerCase() + 'Id', tbl: relationMap[childName] || childName };
        // Fetch all counts in one query: SELECT fk_field, COUNT(*) ... GROUP BY fk_field
        // Supabase REST doesn't support GROUP BY natively, so we do one query and aggregate client-side
        var { data: childRows } = await this.sb.from(fkInfo.tbl).select(fkInfo.fk).in(fkInfo.fk, ids);
        if (childRows) {
          // Build a count map
          var countMap = {};
          for (var cr of childRows) {
            var fkVal = cr[fkInfo.fk];
            countMap[fkVal] = (countMap[fkVal] || 0) + 1;
          }
          for (var row of data) {
            row._count[childName] = countMap[row.id] || 0;
          }
        }
      }
    }

    return data;
  }

  async findUnique({ where, include, select: selectFields } = {}) {
    let selectStr = selectFields
      ? (Array.isArray(selectFields) ? selectFields.join(', ') : selectFields)
      : buildSelect(include);
    let query = this.sb.from(this.tableName).select(selectStr);

    if (where) query = this._applyWhere(query, where);
    query = query.limit(1);

    const { data, error } = await query;
    if (error) throw new Error(`[${this.tableName}/findUnique] ${error.message}`);
    return data?.[0] || null;
  }

  async findFirst({ where, include, orderBy, select: selectFields } = {}) {
    let selectStr = selectFields
      ? (Array.isArray(selectFields) ? selectFields.join(', ') : selectFields)
      : buildSelect(include);
    let query = this.sb.from(this.tableName).select(selectStr);

    if (where) query = this._applyWhere(query, where);
    if (orderBy) query = this._applyOrder(query, orderBy);
    query = query.limit(1);

    const { data, error } = await query;
    if (error) throw new Error(`[${this.tableName}/findFirst] ${error.message}`);
    return data?.[0] || null;
  }

  async create({ data, include, select: selectFields }) {
    // Extract nested create/connect data
    var parts = this._extractNested(data);
    let selectStr = selectFields
      ? (Array.isArray(selectFields) ? selectFields.join(', ') : selectFields)
      : buildSelect(include);

    let query = this.sb.from(this.tableName).insert(parts.flat).select(selectStr);

    const { data: created, error } = await query;
    if (error) throw new Error(`[${this.tableName}/create] ${error.message}`);
    const record = created?.[0] || created;

    // Handle nested creates
    if (record && parts.nested.length > 0) {
      for (const nested of parts.nested) {
        await this._createNested(record, nested);
      }
      // Re-fetch with includes
      if (include && Object.keys(include).some(k => k !== '_count')) {
        const { data: refetched } = await this.sb.from(this.tableName)
          .select(selectStr)
          .eq('id', record.id)
          .single();
        return refetched || record;
      }
    }

    return record;
  }

  async update({ where, data, include, select: selectFields }) {
    let selectStr = selectFields
      ? (Array.isArray(selectFields) ? selectFields.join(', ') : selectFields)
      : buildSelect(include);
    let query = this.sb.from(this.tableName).update(data).select(selectStr);

    if (where) query = this._applyWhere(query, where);

    const { data: updated, error } = await query;
    if (error) throw new Error(`[${this.tableName}/update] ${error.message}`);
    return updated?.[0] || null;
  }

  async delete({ where } = {}) {
    let query = this.sb.from(this.tableName).delete();
    if (where) query = this._applyWhere(query, where);

    const { error } = await query;
    if (error) throw new Error(`[${this.tableName}/delete] ${error.message}`);
  }

  async upsert({ where: _where, update, create, ...rest }) {
    // For DeviceToken: upsert on unique token
    const data = update || create || {};
    const onConflict = _where && Object.keys(_where)[0] || 'token';

    const { data: result, error } = await this.sb.from(this.tableName)
      .upsert(data, { onConflict, ignoreDuplicates: false })
      .select();

    if (error) throw new Error(`[${this.tableName}/upsert] ${error.message}`);
    return result?.[0] || null;
  }

  async count({ where } = {}) {
    let query = this.sb.from(this.tableName).select('id', { count: 'exact', head: true });
    if (where) query = this._applyWhere(query, where);

    const { count, error } = await query;
    if (error) throw new Error(`[${this.tableName}/count] ${error.message}`);
    return count || 0;
  }

  async deleteMany({ where } = {}) {
    let query = this.sb.from(this.tableName).delete();
    if (where) query = this._applyWhere(query, where);

    const { error } = await query;
    if (error) throw new Error(`[${this.tableName}/deleteMany] ${error.message}`);
  }

  async createMany({ data }) {
    const { error } = await this.sb.from(this.tableName).insert(data);
    if (error) throw new Error(`[${this.tableName}/createMany] ${error.message}`);
  }

  // ─── Helpers ───

  _applyWhere(query, where) {
    for (const [key, value] of Object.entries(where)) {
      if (value === undefined || value === null) continue;
      // Handle range filter (e.g. scheduledAt: { lte: new Date() })
      if (typeof value === 'object' && !Array.isArray(value) && !(value instanceof Date)) {
        for (const [op, opVal] of Object.entries(value)) {
          if (op === 'lte') query = query.lte(key, opVal);
          else if (op === 'gte') query = query.gte(key, opVal);
          else if (op === 'lt') query = query.lt(key, opVal);
          else if (op === 'gt') query = query.gt(key, opVal);
          else if (op === 'not') query = query.neq(key, opVal);
          else if (op === 'contains') query = query.ilike(key, `%${opVal}%`);
        }
      } else {
        query = query.eq(key, value);
      }
    }
    return query;
  }

  _applyOrder(query, orderBy) {
    if (!orderBy) return query;
    // Support { field: 'asc' } or [{ field: 'asc' }]
    const entries = Array.isArray(orderBy) ? orderBy : [orderBy];
    for (const entry of entries) {
      for (const [field, dir] of Object.entries(entry)) {
        query = query.order(field, { ascending: dir === 'asc' });
      }
    }
    return query;
  }

  /** Extract nested Prisma create data (e.g., { streams: { create: [...] } }) into flat columns. */
  _extractNested(data) {
    var flat = {};
    var nested = [];
    for (const [key, value] of Object.entries(data)) {
      if (value && typeof value === 'object' && !Array.isArray(value) && value.create) {
        // e.g., streams: { create: [{ name, url }] }
        nested.push({ key, records: Array.isArray(value.create) ? value.create : [value.create] });
      } else if (key === 'streams' && Array.isArray(value)) {
        // Direct array of stream objects
        nested.push({ key, records: value });
      } else {
        flat[key] = value;
      }
    }
    return { flat, nested };
  }

  /** Create nested child records after parent creation. */
  async _createNested(parent, { key, records }) {
    const childTable = relationMap[key] || key;
    const fkMap = {
      streams: { foreignKey: 'eventId', table: 'StreamLink' },
      notifications: { foreignKey: 'sentById', table: 'Notification' },
    };

    var info = fkMap[key];
    if (!info) {
      console.warn('[supabase] Unknown nested create key "' + key + '" — skipping child inserts. Add mapping to fkMap in database.js.');
      return;
    }

    var childRecords = records.map(function(r) {
      var obj = Object.assign({}, r);
      obj[info.foreignKey] = parent.id;
      return obj;
    });

    await this.sb.from(info.table).insert(childRecords);
  }
}

class SupabasePrisma {
  constructor(sb) {
    this.sb = sb;
    // Build model proxies for each table
    for (const [prismaName, tableName] of Object.entries(TABLE_MAP)) {
      this[prismaName] = new SupabaseModel(sb, tableName);
    }
  }

  /** Like prisma.$transaction, but for Supabase, just run sequentially. */
  async $transaction(fn) {
    // pseudo-transaction: pass this same instance
    return fn(this);
  }
}

function getPrisma() {
  if (supabase) return supabase;

  const supabaseUrl = process.env.SUPABASE_URL;
  const supabaseKey = process.env.SUPABASE_SERVICE_ROLE_KEY;

  if (!supabaseUrl || !supabaseKey) {
    console.warn('[supabase] ⚠️  SUPABASE_URL or SUPABASE_SERVICE_ROLE_KEY not set.');
    console.warn('[supabase]     Set both in .env. Database operations will fail.');
  }

  const sb = createClient(supabaseUrl || 'http://localhost:54321', supabaseKey || 'no-key', {
    auth: { autoRefreshToken: false, persistSession: false }
  });

  supabase = new SupabasePrisma(sb);
  return supabase;
}

module.exports = { getPrisma };
