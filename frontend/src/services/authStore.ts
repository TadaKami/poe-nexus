import { defineStore } from "pinia";
import { http } from "./ApiService";
import { session } from "./session";
import type { AuthResponse, UserDto } from "@/types/auth";

interface AuthState{
    user: UserDto | null;
}

export const useAuthStore = defineStore('auth',{
    state: (): AuthState => ({user: null}),
    getters: {
        isAuthenticated: (s) => s.user !== null;
    },
    actions:{
        applySession(res: AuthResponse){
            session.token = res.accessToken;
            this.user = res.user;
        },

        async register(email: string, password: string){
            const {data} = await http.post<AuthResponse>('/auth/register', {email,password});
            this.applySession(data);
        },

        async logout(){
            try{
                await http.post('/auth/logout');
            }finally{
                session.token = null;
                this.user = null;
            }
        }
    }
})