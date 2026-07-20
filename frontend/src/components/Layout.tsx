import { Link, Outlet, useNavigate } from "react-router-dom";

export default function Layout() {
  const navigate = useNavigate();
  return (
    <div className="app-layout">
      <nav className="app-nav">
        <span className="nav-brand">云存储</span>
        <Link to="/files">我的文件</Link>
        <Link to="/share">我的分享</Link>
        <button
          onClick={(e) => {
            e.preventDefault();
            localStorage.removeItem("token");
            navigate("/login");
          }}
        >
          退出
        </button>
      </nav>
      <main>
        <Outlet />
      </main>
    </div>
  );
}
