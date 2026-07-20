import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import type { ShareLinkResponse } from "../types";
import { get } from "../api/client";
import { formatSize } from "../utils/format";

export default function ShareAccessPage() {
  // 获取分享校验码
  const { code } = useParams();

  // 获取分享文件元数据
  const [shareInfo, setShareInfo] = useState<ShareLinkResponse | null>(null);

  // 获取后端返回错误
  const [error, setError] = useState<string>("");

  // 重加载状态
  const [loading, setLoading] = useState<boolean>(true);

  // 重加载检测
  useEffect(() => {
    // 页面加载时获取信息
    get<ShareLinkResponse>(`/api/Share/${code}/info`)
      .then(setShareInfo)
      .catch(() => setError("链接无效或已过期"))
      .finally(() => setLoading(false));
  }, [code]);
  return (
    <div className="share-access-card">
      {loading && <p className="loading-state">加载中...</p>}
      {error && <p className="error-state">{error}</p>}
      {shareInfo && (
        <>
          <h2>分享文件</h2>
          <div className="meta">
            <p>文件名：{shareInfo.filename}</p>
            <p>大小：{formatSize(shareInfo.fileSize)}</p>
            <p>过期时间：{new Date(shareInfo.expiredTime).toLocaleString()}</p>
          </div>
          <a href={`/api/Share/${code}`} className="download-link" download>
            下载文件
          </a>
        </>
      )}
    </div>
  );
}
