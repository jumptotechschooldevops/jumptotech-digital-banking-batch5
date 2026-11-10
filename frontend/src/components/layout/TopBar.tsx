import { useNavigate } from "react-router-dom";
import { useAuth } from "@/hooks/useAuth";
import { Button } from "@/components/ui/Button";

export function TopBar() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  async function handleLogout() {
    await logout.mutateAsync();
    navigate("/login", { replace: true });
  }

  return (
    <header className="flex items-center justify-between border-b border-slate-200 bg-white px-4 py-4 sm:px-6">
      <div>
        <p className="text-sm text-slate-500">Welcome back,</p>
        <p className="font-semibold text-navy-900">
          {user ? `${user.firstName} ${user.lastName}` : ""}
        </p>
      </div>
      <Button variant="ghost" onClick={handleLogout} isLoading={logout.isPending}>
        Log out
      </Button>
    </header>
  );
}
