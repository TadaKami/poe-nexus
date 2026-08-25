// Access токен в памяти (вне localStorage)
let accessToken: string | null = null;

export const session = {
    get token(){
        return accessToken;
    },
    set token(value: string | null){
        accessToken = value;
    }
}