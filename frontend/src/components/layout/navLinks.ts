export interface NavLink {
  to: string;
  label: string;
  adminOnly?: boolean;
}

export const navLinks: NavLink[] = [
  { to: "/dashboard", label: "Dashboard" },
  { to: "/accounts", label: "Accounts" },
  { to: "/transfer", label: "Transfer" },
  { to: "/transactions", label: "Transactions" },
  { to: "/profile", label: "Profile" },
  { to: "/admin", label: "Admin", adminOnly: true },
];
