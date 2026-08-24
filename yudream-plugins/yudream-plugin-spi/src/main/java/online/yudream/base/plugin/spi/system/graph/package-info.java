/**
 * Stable, plugin-scoped resource graph projection capability.
 * The contract accepts complete projections only and never exposes logical-table internals, drivers, or Cypher.
 * Table-free overloads resolve only the unique active logical table authorized for the calling plugin;
 * zero or multiple matches fail without selecting a table.
 */
package online.yudream.base.plugin.spi.system.graph;
