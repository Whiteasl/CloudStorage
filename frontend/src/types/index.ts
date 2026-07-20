// 认证相关接口

export interface LoginRequest {
  username: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  password: string;
  email: string;
}

export interface AuthResponse {
  username: string;
  token: string;
  role: string;
}

// 文件相关接口
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

// 请求体 DTO
export interface CreateFolderRequest {
  folderName: string;
  parentFolderId: number;
}

export interface BatchDeleteRequest {
  ids: number[];
}

// 分享相关
export interface ShareLinkResponse {
  id: number;
  verificationCode: string;
  filename: string;
  fileSize: number;
  isFolder: boolean;
  ownerName: string;
  downloadCount: number;
  downloadLimit: number;
  createdAt: string;
  expiredTime: string;
}
