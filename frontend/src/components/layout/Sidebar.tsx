import { NavLink } from "react-router-dom";
import { useAuth } from "@/hooks/useAuth";
import { navLinks } from "./navLinks";

export function Sidebar() {
  const { user } = useAuth();

  return (
    <aside className="hidden w-64 flex-col border-r border-navy-800 bg-navy-900 px-4 py-6 text-white lg:flex">
      <div className="mb-8 px-2">
        <p className="text-lg font-bold tracking-tight">JumpToTech</p>
        <p className="text-xs text-navy-300">Digital Banking</p>
      </div>
      <nav className="flex flex-1 flex-col gap-1" aria-label="Primary">
        {navLinks
          .filter((link) => !link.adminOnly || user?.role === "ADMIN")
          .map((link) => (
            <NavLink
              key={link.to}
              to={link.to}
              className={({ isActive }) =>
                `rounded-lg px-3 py-2.5 text-sm font-medium transition-colors ${
                  isActive ? "bg-teal-600 text-white" : "text-navy-200 hover:bg-navy-800 hover:text-white"
                }`
              }
            >
              {link.label}
            </NavLink>
          ))}
      </nav>
    </aside>
  );
}
