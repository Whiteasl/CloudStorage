import { Link, Outlet } from "react-router-dom";
import { logout } from "../auth/session";

export default function Layout() {
  return (
    <div className="app-layout">
      <nav className="app-nav">
        <span className="nav-brand">云存储</span>
        <Link to="/files">我的文件</Link>
        <Link to="/share">我的分享</Link>
        <button
          onClick={() => {
            logout();
          }}
        >
          注销
        </button>
      </nav>
      <main>
        <Outlet />
      </main>
    </div>
  );
}
