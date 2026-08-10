import LoginPage from "./pages/LoginPage";
import RegisterPage from "./pages/RegisterPage";
import FilesPage from "./pages/FilesPage";
import ShareAccessPage from "./pages/ShareAccessPage";
import SharePage from "./pages/SharePage";
import ForgotPasswordPage from "./pages/ForgotPasswordPage";
import ResetPasswordPage from "./pages/ResetPasswordPage";
import { Navigate, Route, Routes } from "react-router-dom";
import Layout from "./components/Layout";

function App() {
  const token = localStorage.getItem("token");
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/files" />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/share/:code" element={<ShareAccessPage />} />
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />

      <Route element={token ? <Layout /> : <Navigate to="/login" />}>
        <Route path="/files" element={<FilesPage />} />
        <Route path="/share" element={<SharePage />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />
      </Route>
    </Routes>
  );
}

export default App;
