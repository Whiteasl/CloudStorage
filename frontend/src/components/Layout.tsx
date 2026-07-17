import { Link, Outlet } from "react-router-dom";

export default function Layout() {
  return (
    <div>
      <nav>导航栏</nav>
      <main>
        <Link to="/files"> 我的文件 </Link>
        <Link to="/share"> 我的分享 </Link>
        <a
          href="#"
          onClick={(e) => {
            e.preventDefault();
            localStorage.removeItem("token");
            window.location.href = "/login";
          }}
        >
          退出
        </a>
        <Outlet />
      </main>
    </div>
  );
}
