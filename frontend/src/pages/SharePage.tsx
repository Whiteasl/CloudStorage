import { useEffect, useState } from "react";
import type { FileResponse, ShareLinkResponse } from "../types";
import { get } from "../api/client";

export default function SharePage() {
  // 分享功能
  const [showCreate, setShowCreate] = useState<boolean>(false);
  const [resultCode, setResultCode] = useState<string>("");
  const [shares, setShares] = useState<ShareLinkResponse[]>([]);

  // 监视创建分享文件状态
  useEffect(() => {
    getShareList();
  }, [showCreate]);

  // 获取分享文件列表
  async function getShareList(): Promise<void> {
    setShares(await get<ShareLinkResponse[]>("/share/list"));
  }

  // 获取文件列表

  return (
    <>
      <div className="CreateShare">
        <h2>我的分享</h2>
        {/* 创建分享功能 - 按钮 */}
        <button onClick={() => setShowCreate(!showCreate)}>创建分享</button>

        {/* 创建分享功能 - 表单 */}
        {showCreate && (
          <div className="CreateShareInput">
            <select>文件列表</select>
            <input>下载次数</input>
            <input>过期时间</input>
            <button>确认创建</button>
          </div>
        )}

        {/* 创建成功提示 */}
        {resultCode && <p>分享码：{resultCode}</p>}
      </div>
      <div className="MyShare">
        {/* 分享列表 - 表格 */}

        <table>{shares.length === 0 && <p>你还没有分享过文件呢</p>}</table>
      </div>
    </>
  );
}
