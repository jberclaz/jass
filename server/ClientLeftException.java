

class ClientLeftException extends Exception {
    int clientID;

    public ClientLeftException(int clientID) {
        this.clientID = clientID;
    }

    public int getClientId() {
        return clientID;
    }
}
