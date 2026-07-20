import { useEffect, useState } from "react";
import type { FileResponse, ShareLinkResponse } from "../types";
import { del, get, post } from "../api/client";

export default function SharePage() {
  // 分享功能
  // 显示创建分享Input
  const [showCreate, setShowCreate] = useState<boolean>(false);

  // 接收到的分享校验码
  const [resultCode, setResultCode] = useState<string>("");

  // 分享列表
  const [shares, setShares] = useState<ShareLinkResponse[]>([]);

  // 文件列表
  const [files, setFiles] = useState<FileResponse[]>([]);

  // 被选择的文件的ID
  const [selectedFileId, setSelectedFileId] = useState<number>(0);

  // 下载限制 - 初始：-1
  const [downloadLimit, setDownloadLimit] = useState<number>(-1);

  // 过期时间 - 初始：24小时
  const [expiredHours, setExpiredHours] = useState<number>(24);

  // 监视 Hook
  // 监视创建分享文件状态
  useEffect(() => {
    loadShares();
  }, []);

  // loadShares() 触发时机
  useEffect(() => {
    if (showCreate) loadFiles();
  }, [showCreate]);

  // 函数
  // 加载分享文件列表
  async function loadShares(): Promise<void> {
    setShares(await get<ShareLinkResponse[]>("/share/list"));
  }

  // 加载文件列表
  async function loadFiles(): Promise<void> {
    const fileList: FileResponse[] = await get<FileResponse[]>("/file/list");

    setFiles(fileList.filter((f) => !f.isFolder));
  }

  /** 提交创建分享请求，成功后展示校验码并刷新列表 */
  async function handleCreate(): Promise<void> {
    const result: ShareLinkResponse = await post("/share/create", {
      fileId: selectedFileId,
      downloadLimit: downloadLimit,
      expiredHours: expiredHours,
    });

    setResultCode(result.verificationCode);

    loadShares();
  }

  /** 删除指定分享链接，刷新列表 */
  async function handleDelete(id: number) {
    await del(`/share/delete?shareId=${id}`);

    loadShares();
  }

  /** 复制分享链接到剪贴板 */
  function copyLink(code: string) {
    const shareURL: string = window.location.origin + "/share/" + code;

    navigator.clipboard.writeText(shareURL);
  }

  return (
    <>
      <div className="create-share-section">
        <h2>我的分享</h2>
        {/* 创建分享功能 - 按钮 */}
        <button onClick={() => setShowCreate(!showCreate)}>创建分享</button>

        {/* 创建分享功能 - 表单 */}
        {showCreate && (
          <div className="create-share-form">
            <select
              value={selectedFileId}
              onChange={(e) => setSelectedFileId(Number(e.target.value))}
            >
              <option value={0} disabled>
                请选择文件
              </option>
              {files.map((f) => (
                <option value={f.id} key={f.id}>
                  {f.filename}
                </option>
              ))}
            </select>
            <input
              type="number"
              value={downloadLimit}
              onChange={(e) => setDownloadLimit(Number(e.target.value))}
            />
            <input
              type="number"
              value={expiredHours}
              onChange={(e) => setExpiredHours(Number(e.target.value))}
            />
            <button onClick={handleCreate}>确认创建</button>
          </div>
        )}

        {/* 创建成功提示 */}
        {resultCode && <p className="result-code">分享码：{resultCode}</p>}
      </div>
      <div className="MyShare">
        {/* 分享列表 - 表格 */}

        {shares.length === 0 ? (
          <p className="empty-state">你还没有分享过文件呢</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>文件名</th>
                <th>下载次数</th>
                <th>创建时间</th>
                <th>过期时间</th>
                <th>分享码</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {shares.map((s) => (
                <tr key={s.id}>
                  <td>{s.filename}</td>
                  <td>
                    {s.downloadCount}/
                    {s.downloadLimit === -1 ? "∞" : s.downloadLimit}
                  </td>
                  <td>{new Date(s.createdAt).toLocaleString()}</td>
                  <td>{new Date(s.expiredTime).toLocaleString()}</td>
                  <td>{s.verificationCode}</td>
                  <td>
                    <button onClick={() => copyLink(s.verificationCode)}>
                      复制
                    </button>
                    <button onClick={() => handleDelete(s.id)}>删除</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </>
  );
}
