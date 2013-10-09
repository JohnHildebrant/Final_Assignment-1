

public interface IRhoApiFactory <IApi extends IRhoApiObject>{
    IApi getApiObject(String strID);
}
