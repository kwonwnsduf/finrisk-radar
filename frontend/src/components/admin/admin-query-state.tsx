import { Button } from "@/components/ui/button";
export function AdminQueryState({ loading, error, empty, retry }: { loading: boolean; error: boolean; empty?: boolean; retry: () => void }) {
  if (loading) return <p className="rounded-xl bg-white p-8 text-center text-sm text-slate-500">데이터를 불러오는 중입니다.</p>;
  if (error) return <div className="rounded-xl bg-red-50 p-6 text-center text-sm text-red-700">데이터를 불러오지 못했습니다.<div className="mt-3"><Button variant="outline" onClick={retry}>다시 시도</Button></div></div>;
  if (empty) return <p className="rounded-xl bg-white p-8 text-center text-sm text-slate-500">조건에 맞는 데이터가 없습니다.</p>;
  return null;
}
