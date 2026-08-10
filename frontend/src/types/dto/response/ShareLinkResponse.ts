// 分享链接响应格式

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
