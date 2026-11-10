import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "@/hooks/useAuth";
import { registerSchema, type RegisterFormValues } from "@/schemas/auth";
import { Input } from "@/components/ui/Input";
import { Button } from "@/components/ui/Button";
import { Alert } from "@/components/ui/Alert";
import { ApiError } from "@/lib/apiClient";

export function RegisterPage() {
  const { register: registerUser } = useAuth();
  const navigate = useNavigate();

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<RegisterFormValues>({ resolver: zodResolver(registerSchema) });

  async function onSubmit(values: RegisterFormValues) {
    try {
      await registerUser.mutateAsync({
        email: values.email,
        password: values.password,
        firstName: values.firstName,
        lastName: values.lastName,
        phone: values.phone || undefined,
      });
      navigate("/login", { replace: true, state: { registered: true } });
    } catch (error) {
      if (error instanceof ApiError && error.code === "EMAIL_ALREADY_REGISTERED") {
        setError("email", { message: error.message });
      }
    }
  }

  const topLevelError =
    registerUser.error instanceof ApiError && registerUser.error.code !== "EMAIL_ALREADY_REGISTERED"
      ? registerUser.error.message
      : null;

  return (
    <div className="flex min-h-screen items-center justify-center bg-navy-950 px-4 py-10">
      <div className="w-full max-w-lg rounded-xl bg-white p-8 shadow-xl">
        <div className="mb-6 text-center">
          <p className="text-xl font-bold text-navy-900">JumpToTech</p>
          <p className="text-sm text-slate-500">Digital Banking</p>
        </div>

        <h1 className="mb-6 text-center text-lg font-semibold text-navy-900">Create your account</h1>

        {topLevelError && (
          <div className="mb-4">
            <Alert variant="error">{topLevelError}</Alert>
          </div>
        )}

        <form className="flex flex-col gap-4" onSubmit={handleSubmit(onSubmit)} noValidate>
          <div className="grid gap-4 sm:grid-cols-2">
            <Input label="First name" autoComplete="given-name" error={errors.firstName?.message} {...register("firstName")} />
            <Input label="Last name" autoComplete="family-name" error={errors.lastName?.message} {...register("lastName")} />
          </div>
          <Input label="Email address" type="email" autoComplete="email" error={errors.email?.message} {...register("email")} />
          <Input
            label="Phone (optional)"
            type="tel"
            autoComplete="tel"
            error={errors.phone?.message}
            {...register("phone")}
          />
          <Input
            label="Password"
            type="password"
            autoComplete="new-password"
            hint="At least 10 characters."
            error={errors.password?.message}
            {...register("password")}
          />
          <Input
            label="Confirm password"
            type="password"
            autoComplete="new-password"
            error={errors.confirmPassword?.message}
            {...register("confirmPassword")}
          />
          <Button type="submit" isLoading={registerUser.isPending} className="mt-2 w-full">
            Create account
          </Button>
        </form>

        <p className="mt-6 text-center text-sm text-slate-500">
          Already have an account?{" "}
          <Link to="/login" className="font-medium text-teal-700 hover:underline">
            Log in
          </Link>
        </p>
      </div>
    </div>
  );
}
