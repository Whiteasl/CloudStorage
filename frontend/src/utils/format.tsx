export function formatSize(size: number): string {
  const sizes: string[] = ["B", "KB", "MB", "GB"];

  let index: number = 0;

  while (size >= 1024) {
    size = size / 1024;
    index++;
  }

  return size.toFixed(1) + sizes[index];
}
