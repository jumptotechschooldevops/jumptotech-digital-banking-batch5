import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useProfile, useUpdateProfile } from "@/hooks/useProfile";
import { profileSchema, type ProfileFormValues } from "@/schemas/profile";
import { Card, CardHeader } from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";
import { Button } from "@/components/ui/Button";
import { Alert } from "@/components/ui/Alert";
import { LoadingSpinner } from "@/components/ui/LoadingSpinner";

export function ProfilePage() {
  const { data: profile, isLoading, isError } = useProfile();
  const updateProfile = useUpdateProfile();
  const [savedAt, setSavedAt] = useState<number | null>(null);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<ProfileFormValues>({ resolver: zodResolver(profileSchema) });

  useEffect(() => {
    if (profile) {
      reset({
        phone: profile.phone ?? "",
        addressLine1: profile.addressLine1 ?? "",
        addressLine2: profile.addressLine2 ?? "",
        city: profile.city ?? "",
        state: profile.state ?? "",
        postalCode: profile.postalCode ?? "",
      });
    }
  }, [profile, reset]);

  async function onSubmit(values: ProfileFormValues) {
    await updateProfile.mutateAsync(values);
    setSavedAt(Date.now());
  }

  if (isLoading) {
    return <LoadingSpinner label="Loading your profile…" />;
  }

  if (isError || !profile) {
    return <Alert variant="error">We couldn&apos;t load your profile right now. Please try again shortly.</Alert>;
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-bold text-navy-900">Profile</h1>
        <p className="text-slate-500">Your name and email are on file and can&apos;t be changed here.</p>
      </div>

      <Card className="max-w-xl">
        <CardHeader title="Account details" />
        <dl className="mb-6 grid grid-cols-2 gap-y-2 text-sm">
          <dt className="text-slate-500">Name</dt>
          <dd className="text-right font-medium text-navy-900">
            {profile.firstName} {profile.lastName}
          </dd>
          <dt className="text-slate-500">Email</dt>
          <dd className="text-right font-medium text-navy-900">{profile.email}</dd>
        </dl>

        {savedAt && (
          <div className="mb-4">
            <Alert variant="success">Profile updated successfully.</Alert>
          </div>
        )}

        <form className="flex flex-col gap-4" onSubmit={handleSubmit(onSubmit)} noValidate>
          <Input label="Phone" type="tel" error={errors.phone?.message} {...register("phone")} />
          <Input label="Address line 1" error={errors.addressLine1?.message} {...register("addressLine1")} />
          <Input label="Address line 2" error={errors.addressLine2?.message} {...register("addressLine2")} />
          <div className="grid gap-4 sm:grid-cols-3">
            <Input label="City" error={errors.city?.message} {...register("city")} />
            <Input label="State" error={errors.state?.message} {...register("state")} />
            <Input label="Postal code" error={errors.postalCode?.message} {...register("postalCode")} />
          </div>
          <Button type="submit" isLoading={updateProfile.isPending} className="mt-2 w-full sm:w-auto">
            Save changes
          </Button>
        </form>
      </Card>
    </div>
  );
}
