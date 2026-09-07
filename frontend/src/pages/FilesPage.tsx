import { useEffect, useRef, useState } from "react";
import type { FileResponse } from "../types/dto/response/FileResponse";
import { ApiError, BASE_URL, del, get, post, put, upload } from "../api/client";
import { formatSize } from "../utils/format";
import type { CompressRequest } from "../types/dto/request/CompressRequest";
import type { BatchDeleteRequest } from "../types/dto/request/BatchDeleteRequest";
import type { CreateFolderRequest } from "../types/dto/request/CreateFolderRequest";

export default function FilesPage() {
  const [files, setFiles] = useState<FileResponse[]>([]); // 当前目录的文件列表

  const [currentFolder, setCurrentFolder] = useState<number | null>(null); // 当前所在目录ID，null = 根目录

  const [folderStack, setFolderStack] = useState<number[]>([]); // 存储的父文件夹ID，用于推导路径、返回某个上级文件夹功能

  const [selected, setSelected] = useState<Set<number>>(new Set<number>()); // 当前选择的文件 ID 集合

  const [searchKeyword, setSearchKeyword] = useState<string>(""); // 搜索关键词
  const [showSearch, setShowSearch] = useState<boolean>(false); // 显示/隐藏搜索框

  const [loading, setLoading] = useState<boolean>(false); // 加载中状态

  const [renameId, setRenameId] = useState<number | null>(null); // 重命名文件ID
  const [newName, setNewName] = useState<string>(""); // 重命名-设置新名

  const [createFolder, setCreateFolder] = useState<string>(""); // 创建目录-设置目录名
  const [creatingFolder, setCreatingFolder] = useState<boolean>(false); // 创建目录-状态检查

  const [archiveName, setArchiveName] = useState<string>("");

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
      standardRequestErrorMessage(e);
    } finally {
      // 重置加载中状态
      setLoading(false);
    }
  }

  async function handleSearch(): Promise<void> {
    // 搜索功能实现
    let data: FileResponse[];
    try {
      const key = encodeURIComponent(searchKeyword);
      data = await get<FileResponse[]>(`/file/search?keyword=${key}`);
    } catch (error) {
      standardRequestErrorMessage(error);
      return;
    }

    setFiles(data);
  }

  async function submitUpload(file: File | undefined) {
    // 上传文件

    if (!file) return; // 如果没有选择文件则退出

    if (isDuplicate(file.name)) {
      // 重名检测
      alert("文件夹内已有相同文件名的文件");
      return;
    }

    const formData = new FormData();

    formData.append("file", file);

    if (currentFolder !== null)
      formData.append("parentFolderId", String(currentFolder));

    try {
      await upload("/file/upload", formData).then(() =>
        loadFiles(currentFolder),
      );
    } catch (error) {
      standardRequestErrorMessage(error);
    }
  }

  async function submitCreateFolder(): Promise<void> {
    // 创建目录API提交

    if (createFolder === "") {
      alert("目录名不能为空");
      return;
    }

    if (isDuplicate(createFolder)) {
      alert("文件夹下已有相同文件名的文件");
      setCreatingFolder(false);
      setCreateFolder("");
      return;
    }

    const body: CreateFolderRequest = {
      folderName: createFolder,
      parentFolderId: currentFolder,
    };

    try {
      await post(`/file/folder`, body);
    } catch (error) {
      standardRequestErrorMessage(error);
      return;
    }
    setCreateFolder("");

    loadFiles(currentFolder);
  }

  async function submitRename(excludeId: number): Promise<void> {
    // 重命名API提交
    if (isDuplicate(newName, excludeId)) {
      alert("文件夹下已有同名文件");
      setNewName("");
      setRenameId(null);
      return;
    }

    if (newName === "") {
      alert("文件名不能为空");
      return;
    }

    try {
      const rename = encodeURIComponent(newName);
      await put(`/file/rename?fileId=${renameId}&newName=${rename}`);
    } catch (error) {
      standardRequestErrorMessage(error);
      return;
    }

    setRenameId(null);
    loadFiles(currentFolder);
  }

  async function delFile(fileId: number): Promise<void> {
    // 删除文件
    try {
      await del(`/file/delete?fileId=${fileId}`);
    } catch (error) {
      standardRequestErrorMessage(error);
      return;
    }
    loadFiles(currentFolder);
  }

  async function batchDelFile(): Promise<void> {
    // 批量删除
    const body: BatchDeleteRequest = { ids: Array.from(selected) };

    let failed;
    try {
      failed = await post<number[]>("/file/batch-delete", body);
    } catch (error) {
      standardRequestErrorMessage(error);
      return;
    }

    // 如果返回不为空，则说明有文件未删除成功
    if (failed && failed.length > 0) {
      const names = files
        .filter((f) => failed.includes(f.id))
        .map((f) => f.filename)
        .join("、");
      alert(
        `已删除 ${selected.size - failed.length} 个文件，失败 ${failed.length} 个：${names}`,
      );
    }

    setSelected(new Set());
    loadFiles(currentFolder);
  }

  async function compress(): Promise<void> {
    // 压缩功能
    const body: CompressRequest = {
      ids: Array.from(selected),
      folderId: currentFolder,
      archiveName: archiveName,
    };

    try {
      await post("/file/compress", body);
    } catch (error) {
      if (error instanceof ApiError) {
        switch (error.status) {
          case 400:
            return alert("文件夹下已有同名文件");
          case 429:
            return alert("请求过于频繁，请稍后再试");
          case 409:
            return alert("配额不足");
          default:
            return alert("请求异常，请重试");
        }
      } else {
        return alert("网络异常，请检查连接");
      }
    }
    setArchiveName("");

    loadFiles(currentFolder);
  }

  // 临时用的统一错误信息展示，后续添加弹窗后修改
  function standardRequestErrorMessage(error: unknown) {
    if (error instanceof ApiError) {
      switch (error.status) {
        case 400:
          return alert("请求参数有误");
        case 429:
          return alert("请求过于频繁，请稍后再试");
        default:
          return alert("请求异常，请重试");
      }
    } else {
      return alert("网络异常，请检查连接");
    }
  }

  function getCurrentTime(): string {
    const now = new Date();

    const year = now.getFullYear();
    const month = String(now.getMonth() + 1).padStart(2, "0");
    const day = String(now.getDate()).padStart(2, "0");
    const hours = String(now.getHours()).padStart(2, "0");
    const minutes = String(now.getMinutes()).padStart(2, "0");
    const seconds = String(now.getSeconds()).padStart(2, "0");

    // 格式：2026-09-06-14-30-25
    return `${year}-${month}-${day}-${hours}-${minutes}-${seconds}`;
  }

  function toggleSelect(fileId: number) {
    // 切换文件选择状态
    setSelected((prev) => {
      const next = new Set(prev);
      next.has(fileId) ? next.delete(fileId) : next.add(fileId);
      return next;
    });
  }

  // 同名检查
  function isDuplicate(name: string, excludeId?: number): boolean {
    if (showSearch) return false;

    return files.some((f) => f.filename === name && f.id !== excludeId);
  }

  // 弹出栈顶   用于 返回上级目录 功能
  function goUp(): void {
    // 弹出栈顶
    setFolderStack(folderStack.slice(0, -1));

    // 获取当前文件夹ID
    const currentParent = folderStack[folderStack.length - 1];

    // 设置当前所在文件夹ID
    setCurrentFolder(currentParent);

    // 清空选中文件
    setSelected(new Set());
  }

  // 清空堆栈   用于 返回根目录 功能
  function goRoot(): void {
    setFolderStack([]);
    setSelected(new Set());
    setCurrentFolder(null);
  }

  return (
    <div className="files-pages">
      <div className="breadcrumbs">
        {/* 面包屑 */}
        <button onClick={() => goRoot()} disabled={folderStack.length === 0}>
          返回根目录
        </button>
        <button onClick={() => goUp()} disabled={folderStack.length === 0}>
          返回上级目录
        </button>
      </div>

      {/* 上传文件 */}
      <div className="toolbar">
        <input
          type="file"
          ref={fileInputRef}
          style={{ display: "none" }}
          onChange={(e) => {
            const file = e.target.files?.[0]; // 获取选中的文件

            submitUpload(file);

            e.target.value = "";
          }}
        />

        <button onClick={() => fileInputRef.current?.click()}>上传文件</button>

        {/* 创建目录 */}
        {creatingFolder === false ? (
          <button
            onClick={() => {
              setCreatingFolder(true);
              setCreateFolder("NewFolder");
            }}
          >
            创建文件夹
          </button>
        ) : (
          <>
            <input
              type="text"
              value={createFolder}
              placeholder={createFolder}
              onChange={(e) => {
                setCreatingFolder(true);
                setCreateFolder(e.target.value);
              }}
              onKeyDown={(e) => {
                if (e.key === "Enter") submitCreateFolder();
              }}
            />
            <button
              onClick={() => {
                setCreateFolder("");
                setCreatingFolder(false);
              }}
            >
              取消
            </button>
          </>
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
            取消
          </button>
        ) : null}
      </div>

      {loading && <p className="loading-state">加载中...</p>}

      {/* 用户文件显示 */}
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
                  // 重命名功能
                  <input
                    type="text"
                    value={newName}
                    onChange={(e) => setNewName(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === "Enter") {
                        submitRename(file.id);
                      }
                    }}
                  />
                ) : file.isFolder ? (
                  // 文件夹逻辑
                  <button
                    onClick={() => {
                      if (currentFolder !== null)
                        setFolderStack((prev) => [...prev, currentFolder]);
                      setCurrentFolder(file.id);
                      setSelected(new Set());
                    }}
                  >
                    {file.filename}
                  </button>
                ) : (
                  // 文件逻辑
                  file.filename
                )}
              </td>
              <td>{file.isFolder ? "-" : formatSize(file.fileSize)}</td>
              <td>{new Date(file.updatedAt).toLocaleString()}</td>
              {file.isFolder ? null : (
                <a
                  href={`${BASE_URL}/file/download?fileId=${file.id}`}
                  download
                >
                  下载
                </a>
              )}
              <button
                onClick={() => {
                  if (
                    window.confirm(
                      "是否删除，如果你删除的是文件夹，文件夹内的内容将被一并删除",
                    )
                  )
                    delFile(file.id);
                }}
              >
                删除
              </button>
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
          <button
            onClick={() => {
              if (
                window.confirm(
                  "是否删除选中文件/文件夹，文件夹内容将被一并删除",
                )
              ) {
                batchDelFile();
              }
            }}
          >
            批量删除
          </button>

          {selected.size === 0 ? null : archiveName === "" ? (
            <button onClick={() => setArchiveName(getCurrentTime())}>
              压缩下载
            </button>
          ) : (
            <>
              <input
                type="text"
                value={archiveName}
                onChange={(e) => setArchiveName(e.target.value)}
              />
              <button
                onClick={() => {
                  if (archiveName !== "") compress();
                  else return alert("压缩包名字不能为空");
                }}
              >
                确定
              </button>
            </>
          )}
        </div>
      )}
    </div>
  );
}
