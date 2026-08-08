export interface GridShape {
  cols: number;
  rows: number;
  featureFirst: boolean;
}


export function computeGrid(count: number, portrait = false): GridShape {
  if (count <= 1) return { cols: 1, rows: 1, featureFirst: false };
  if (count === 2) {
    return portrait
      ? { cols: 1, rows: 2, featureFirst: false }
      : { cols: 2, rows: 1, featureFirst: false };
  }
  if (count <= 4) return { cols: 2, rows: 2, featureFirst: false };
  if (count <= 6) {
    return portrait
      ? { cols: 2, rows: 3, featureFirst: false }
      : { cols: 3, rows: 2, featureFirst: false };
  }
  return { cols: 3, rows: 3, featureFirst: true };
}

export function gridTemplateStyle(shape: GridShape): React.CSSProperties {
  return {
    gridTemplateColumns: `repeat(${shape.cols}, minmax(0, 1fr))`,
    gridTemplateRows: `repeat(${shape.rows}, minmax(0, 1fr))`,
  };
}