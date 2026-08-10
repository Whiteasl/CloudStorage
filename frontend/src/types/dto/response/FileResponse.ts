// 文件响应格式

export interface FileResponse {
  id: number;
  filename: string;
  filePath: string;
  fileSize: number;
  contentType: string | null;
  isFolder: boolean;
  parentFolderId: number | null;
  createdAt: string;
  updatedAt: string;
}
