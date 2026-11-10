import { NavLink } from "react-router-dom";
import { useAuth } from "@/hooks/useAuth";
import { navLinks } from "./navLinks";

export function MobileNav() {
  const { user } = useAuth();

  return (
    <nav
      className="fixed inset-x-0 bottom-0 z-40 flex justify-between border-t border-slate-200 bg-white px-2 py-2 lg:hidden"
      aria-label="Primary"
    >
      {navLinks
        .filter((link) => !link.adminOnly || user?.role === "ADMIN")
        .map((link) => (
          <NavLink
            key={link.to}
            to={link.to}
            className={({ isActive }) =>
              `flex-1 rounded-lg px-1 py-2 text-center text-[11px] font-medium ${
                isActive ? "text-teal-700" : "text-slate-500"
              }`
            }
          >
            {link.label}
          </NavLink>
        ))}
    </nav>
  );
}
