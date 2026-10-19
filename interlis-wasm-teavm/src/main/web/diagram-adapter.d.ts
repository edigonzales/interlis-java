export interface DiagramAnchor {
  readonly source: string;
  readonly range?: unknown;
}

export interface DiagramNode {
  readonly id: string;
  readonly kind: string;
  readonly containerId?: string;
  readonly label: string;
  readonly canonicalName: string;
  readonly status: string;
  readonly compartments: readonly unknown[];
  readonly anchor?: DiagramAnchor | null;
}

export interface DiagramEdge {
  readonly id: string;
  readonly kind: string;
  readonly source: unknown;
  readonly target: unknown;
  readonly sourceEnd: unknown;
  readonly targetEnd: unknown;
  readonly label: string;
  readonly anchor?: DiagramAnchor | null;
}

export interface DiagramView {
  readonly schemaVersion: "1.0";
  readonly snapshotId: string;
  readonly sourceRevisions: Readonly<Record<string, string>>;
  readonly completeness: string;
  readonly nodes: readonly DiagramNode[];
  readonly containers: readonly unknown[];
  readonly edges: readonly DiagramEdge[];
  /** All edges sharing a semantic origin (n-ary/attributed association). */
  readonly associations: ReadonlyMap<string, readonly DiagramEdge[]>;
  readonly notices: readonly unknown[];
}

export interface DiagramAdapter {
  adapt(raw: unknown): DiagramView;
  isStale(view: DiagramView, currentSnapshotId: string): boolean;
}

export const diagramAdapter: DiagramAdapter;
