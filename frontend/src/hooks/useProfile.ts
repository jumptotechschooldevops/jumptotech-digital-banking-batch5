import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { fetchMyProfile, updateMyProfile, type UpdateProfilePayload } from "@/api/customers";

export function useProfile() {
  return useQuery({
    queryKey: ["profile", "me"],
    queryFn: fetchMyProfile,
  });
}

export function useUpdateProfile() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: UpdateProfilePayload) => updateMyProfile(payload),
    onSuccess: (updated) => {
      queryClient.setQueryData(["profile", "me"], updated);
    },
  });
}
