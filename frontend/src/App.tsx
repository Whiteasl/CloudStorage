import LoginPage from "./pages/LoginPage";
import RegisterPage from "./pages/RegisterPage";
import FilesPage from "./pages/FilesPage";
import ShareAccessPage from "./pages/ShareAccessPage";
import SharePage from "./pages/SharePage";
import ForgotPasswordPage from "./pages/ForgotPasswordPage";
import { Navigate, Route, Routes } from "react-router-dom";
import Layout from "./components/Layout";
import ForgotAndResetPasswordPage from "./pages/ForgotAndResetPasswordPage";
import { useAuth } from "./auth/authStore";
import { useEffect } from "react";
import { probeSession } from "./auth/session";

function RequireAuth() {
  const { status } = useAuth();

  useEffect(() => {
    if (status === "unknown") {
      probeSession();
    }
  }, [status]);

  switch (status) {
    case "anonymous":
      return <Navigate to="/login" replace />;
    case "authenticated":
      return <Layout />;
    case "unknown":
      return null;
  }
}

function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/files" />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/share/:code" element={<ShareAccessPage />} />
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route
        path="/forgot-password/reset"
        element={<ForgotAndResetPasswordPage />}
      />

      <Route element={<RequireAuth />}>
        <Route path="/files" element={<FilesPage />} />
        <Route path="/share" element={<SharePage />} />
        {/* <Route path="/reset-password" element={<ResetPasswordPage />} /> */}
      </Route>
    </Routes>
  );
}

export default App;
