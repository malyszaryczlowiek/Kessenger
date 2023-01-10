export interface Invitation {
    login: string;
    toUserId: string;
    chatName: string;
    chatId: string;
    sendingTime: number;
    serverTime: number;
    myJoiningOffset: number;
};