// 压缩功能请求体

export interface CompressRequest {
  ids: number[];
  folderId: number | null;
  archiveName: string;
}
