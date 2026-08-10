import { useEffect, useRef, useState } from "react";
import type { FileResponse } from "../types/dto/response/FileResponse";
import { del, get, post, put, upload } from "../api/client";
import { formatSize } from "../utils/format";

export default function FilesPage() {
  const [files, setFiles] = useState<FileResponse[]>([]); // 当前目录的文件列表

  const [currentFolder, setCurrentFolder] = useState<number | null>(null); // 当前所在目录ID，null = 根目录

  const [selected, setSelected] = useState<Set<number>>(new Set<number>()); // 当前选择的文件 ID 集合

  const [searchKeyword, setSearchKeyword] = useState<string>(""); // 搜索关键词
  const [showSearch, setShowSearch] = useState<boolean>(false); // 显示/隐藏搜索框

  const [loading, setLoading] = useState<boolean>(false); // 加载中状态

  const [renameId, setRenameId] = useState<number | null>(null); // 重命名文件ID
  const [newName, setNewName] = useState<string>(""); // 重命名-设置新名

  const [createFolder, setCreateFolder] = useState<string>(""); // 创建目录-设置目录名

  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    loadFiles(currentFolder);
  }, [currentFolder]);

  async function loadFiles(folderId: number | null): Promise<void> {
    // 设置加载状态
    setLoading(true);

    try {
      const param = folderId !== null ? `?parentFolderId=${folderId}` : "";
      const data = await get<FileResponse[]>(`/file/list${param}`);

      // 设置当前目录的文件列表
      setFiles(data);
    } catch (e) {
      console.error(e);
    } finally {
      // 重置加载中状态
      setLoading(false);
    }
  }

  async function handleSearch(): Promise<void> {
    // 搜索功能实现
    const data = await get<FileResponse[]>(
      `/file/search?keyword=${searchKeyword}`,
    );

    setFiles(data);
  }

  async function submitCreateFolder(): Promise<void> {
    // 创建目录API提交
    await post(`/file/folder`, {
      folderName: createFolder,
      parentFolderId: currentFolder,
    });
    setCreateFolder("");
    loadFiles(currentFolder);
  }

  async function submitRename(): Promise<void> {
    // 重命名API提交
    await put(`/file/rename?fileId=${renameId}&newName=${newName}`);
    setRenameId(null);
    loadFiles(currentFolder);
  }

  async function delFile(fileId: number): Promise<void> {
    // 删除文件
    await del(`/file/delete?fileId=${fileId}`);
    loadFiles(currentFolder);
  }

  async function batchDelFile(): Promise<void> {
    // 批量删除
    for (const id of selected) {
      await del(`/file/delete?fileId=${id}`);
    }
    loadFiles(currentFolder);
  }

  function compress(): void {
    window.open(
      `/api/file/compress?fileIds=${Array.from(selected).join(",")}&archiveName=${String(Date.now())}`,
    );
  }

  function toggleSelect(fileId: number) {
    setSelected((prev) => {
      const next = new Set(prev);
      next.has(fileId) ? next.delete(fileId) : next.add(fileId);
      return next;
    });
  }

  return (
    <div className="files-pages">
      <div className="breadcrumbs">
        {/* 面包屑 */}
        <button
          onClick={() => {
            setCurrentFolder(null);
          }}
        >
          返回根目录
        </button>
        {currentFolder !== null && (
          <button
            onClick={() => {
              setCurrentFolder(files[0]?.parentFolderId ?? null);
            }}
          >
            返回上级目录
          </button>
        )}
      </div>

      {/* 上传文件 */}
      <div className="toolbar">
        <input
          type="file"
          ref={fileInputRef}
          style={{ display: "none" }}
          onChange={(e) => {
            const file = e.target.files?.[0]; // 获取选中的文件
            if (!file) return; // 如果没有选择文件则退出
            const formData = new FormData();
            formData.append("file", file);
            if (currentFolder !== null)
              formData.append("parentFolderId", String(currentFolder));
            upload("/file/upload", formData).then(() =>
              loadFiles(currentFolder),
            );

            e.target.value = "";
          }}
        />

        <button onClick={() => fileInputRef.current?.click()}>上传文件</button>

        {/* 创建目录 */}
        {createFolder === "" ? (
          <button
            onClick={() => {
              setCreateFolder("NewFolder");
            }}
          >
            创建文件夹
          </button>
        ) : (
          <input
            type="text"
            value={createFolder}
            onChange={(e) => setCreateFolder(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") submitCreateFolder();
            }}
          ></input>
        )}

        {/* 搜索框 */}
        {!showSearch ? (
          <button onClick={() => setShowSearch(true)}>搜索</button>
        ) : (
          <input
            type="text"
            value={searchKeyword}
            onChange={(e) => setSearchKeyword(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") handleSearch();
            }}
          ></input>
        )}
        {searchKeyword !== "" ? (
          <button
            onClick={() => {
              setSearchKeyword("");
              setShowSearch(false);
              loadFiles(currentFolder);
            }}
          >
            清除
          </button>
        ) : null}
      </div>

      {loading && <p className="loading-state">加载中...</p>}

      <table>
        <thead>
          <tr>
            <th>
              <input
                type="checkbox"
                checked={selected.size === files.length && files.length > 0}
                onChange={() => {
                  selected.size === files.length
                    ? setSelected(new Set())
                    : setSelected(new Set(files.map((f) => f.id)));
                }}
              />
            </th>
            <th>文件名</th>
            <th>文件大小</th>
            <th>修改时间</th>
            <th>操作</th>
          </tr>
        </thead>

        <tbody>
          {files.map((file) => (
            <tr key={file.id}>
              <td>
                <input
                  type="checkbox"
                  checked={selected.has(file.id)}
                  onChange={() => toggleSelect(file.id)}
                />
              </td>
              <td>
                {renameId === file.id ? (
                  <input
                    type="text"
                    onChange={(e) => setNewName(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === "Enter") submitRename();
                    }}
                  />
                ) : file.isFolder ? (
                  <button onClick={() => setCurrentFolder(file.id)}>
                    {file.filename}
                  </button>
                ) : (
                  file.filename
                )}
              </td>
              <td>{file.isFolder ? "-" : formatSize(file.fileSize)}</td>
              <td>{new Date(file.updatedAt).toLocaleString()}</td>
              {file.isFolder ? null : (
                <a href={`/api/file/download?fileId=${file.id}`} download>
                  下载
                </a>
              )}
              <button onClick={() => delFile(file.id)}>删除</button>
              <button
                onClick={() => {
                  setRenameId(file.id);
                  setNewName(file.filename); // 预填当前文件名
                }}
              >
                重命名
              </button>
            </tr>
          ))}
        </tbody>
      </table>

      {!loading && files.length === 0 && (
        <p className="empty-state">目录为空</p>
      )}

      {selected.size > 0 && (
        <div className="selection-bar">
          <span>已选择 {selected.size} 个文件</span>
          <button onClick={() => batchDelFile()}>批量删除</button>

          <button onClick={compress}>压缩下载</button>
        </div>
      )}
    </div>
  );
}
