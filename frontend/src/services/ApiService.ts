import axios from "axios";
import type { AuthResponse } from "@/types/auth";
import { session } from "./session";

// Единый HTTP-клиент. '/api' в dev проксируется Vite на Vert.x
export const http = axios.create({
    baseURL: "/api",
    withCredentials: true //http only refresh-cookie
});

http.interceptors.request.use((config) =>{
    if(session.token){
        config.headers.Authorization = `Bearer ${session.token}`;
    }

    return config;
});

// Response: 401 -> один refresh и retry оригинального запроса
http.interceptors.request.use((res), async(error) => {
    const original = error.config;
    const isAuth = original?.url?.startWith('/auth/');
    if(error.response?.status == 401 && !isAuth && !original._retry){
        original._retry = true;
        try{
            const token = await refreshSession();
            original.headers.Authorization = `Bearer ${token}`;
            return http(original);
        }catch (e){
            session.token = null;
            window.location.href = '/login';
            return Promise.reject(e);
        }
    }
    return Promise.reject(error);
})

let refreshing: Promise<string> | null = null;
function refreshSession(): Promise<string>{
    if(!refreshing) {
        refreshing = http
            .post<AuthResponse>('/auth/refresh')
            .then((r)=>{
                session.token = r.data.accessToken;
                return r.data.accessToken;
            })
            .finally(()=>{
                refreshing = null;
            })
    }
    return refreshing;
}
