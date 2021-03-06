package com.db.shipit.repositories;

import com.db.shipit.models.*;
import com.db.shipit.models.Package;
import com.db.shipit.utils.CourierPicker;
import com.db.shipit.utils.DatePicker;
import com.db.shipit.utils.RandomID;
import com.sun.deploy.security.SelectableSecurityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.db.shipit.ShipitApplication.currentUser;

@Repository
public class PackageRepository {

    @Autowired
    ReportRepository reportRepository;
    @Autowired
    CustomerRepository customerRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public List<Package> getAllPackages(Map<String, Boolean> modifications) {
        String id = currentUser.getID();

        List<Package> packages = jdbcTemplate.query("SELECT * FROM Package WHERE receiver_id = ? OR sender_id = ?", new Object[]{id, id},new BeanPropertyRowMapper(Package.class));
        return packages;
    }
    public List<Package> getAllBranchPackages(Map<String, Boolean> modifications) {
        String id = currentUser.getID();

        List<Package> packages = jdbcTemplate.query("SELECT package_id,delivery_date,send_date,status, from_city, curr_city, to_city FROM Package WHERE from_city in (SELECT city_name FROM Branch, CustomerService WHERE name=branch_name AND ID = ? )  OR curr_city in (SELECT city_name FROM Branch,CustomerService WHERE name=branch_name AND ID = ? ) OR to_city in (SELECT city_name FROM Branch,CustomerService WHERE name=branch_name AND ID = ? )", new Object[]{id,id, id},new BeanPropertyRowMapper(Package.class));
        return packages;
    }

    public String getAPackageCourier(String package_id) {
        String id = currentUser.getID();

        List<Package> packages = jdbcTemplate.query("SELECT * FROM Package WHERE package_id = ? ", new Object[]{package_id },new BeanPropertyRowMapper(Package.class));
        return packages.get(0).getCourier();
    }

    public List<Map<String, Object>> getTopSenders(){
        List<Map<String, Object>> customers = jdbcTemplate.queryForList("SELECT sender_id, COUNT(package_id) AS total_packages FROM Package GROUP BY sender_id ORDER BY total_packages DESC");


        return customers;
    }

    public Map<String, String> getBranchStatistics (){
        Map<String, String> list = (Map<String, String>) jdbcTemplate.query("SELECT * FROM (SELECT curr_city, status, COUNT(package_id) AS total_packages_of_a_status FROM (select package_id, curr_city, status from Package) GROUP BY curr_city, status) NATURAL JOIN", new BeanPropertyRowMapper(Package.class));
        return list;
    }

    public void commitPackage(Package packet) {
        //setPropertiesOfToInsert(packet);

        jdbcTemplate.update("INSERT INTO Package VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                packet.getPackage_id(),
                packet.getReceiver_id(),
                packet.getSender_id(),
                packet.getDelivery_date(),
                packet.getSend_date(),
                packet.getPayment_side(),
                packet.getPayment_status(),
                packet.getDelivery_type(),
                packet.getStatus(),
                packet.getPackage_type(),
                packet.getCourier(),
                packet.getFrom_city(),
                packet.getCurr_city(),
                packet.getTo_city(),
                packet.getCost());
    }

    public void moveForward(String package_id)
    {
        List<Package> packages = jdbcTemplate.query("SELECT * FROM Branch B, Package P WHERE B.city_name = P.from_city AND P.package_id = ?", new Object[]{package_id },new BeanPropertyRowMapper(Package.class));
        List<Route> routes = jdbcTemplate.query("SELECT R.from_city, R.hub, R.to_city FROM Route AS R ,Package AS P WHERE P.from_city = R.from_city AND R.to_city=P.to_city  AND P.package_id = ? ", new Object[]{package_id },new BeanPropertyRowMapper(Route.class));
        String newCurrentCity="";
        String destination = "";
        for (int i =0;i<routes.size();i++) {
                if(routes.get(i).getHub().equals(packages.get(0).getCurr_city())) {
                    newCurrentCity = packages.get(0).getTo_city();
                    destination = packages.get(0).getTo_city();
                    break;
                }
                else if ((routes.get(i).getFrom_city()).equals(packages.get(0).getCurr_city())) {

                    if((routes.get(i).getHub()).equals("null"))
                        newCurrentCity = packages.get(0).getTo_city();
                    else
                        newCurrentCity = routes.get(i).getHub();
                    break;


                }
        }
        if(newCurrentCity.equals(""))
            System.out.println("No update");
      else{
          if (destination.equals(newCurrentCity)){
            jdbcTemplate.update("UPDATE Package SET status = ? WHERE package_id = ? ; ",new Object[]{"onBranch",package_id} );
          }
          else {
            jdbcTemplate.update("UPDATE Package SET status = ? WHERE package_id = ? ; ",new Object[]{"onTransfer",package_id} );
          }
          jdbcTemplate.update("UPDATE Package SET curr_city = ? WHERE package_id = ? ; ",new Object[]{newCurrentCity,package_id} );
        }



    }
public List<Boolean> getIfReportExist(List<Package > packages){
   ArrayList<Boolean> bools = new ArrayList<Boolean>();
    for (int i =0; i<packages.size();i++){
        List<Report> reports = jdbcTemplate.query("SELECT * FROM Report WHERE package_id = ?", new Object[]{packages.get(i).getPackage_id()}, new BeanPropertyRowMapper(Report.class));
        bools.add((reports.size() == 0));
    }

    return bools;
}
    public void updatePackageStatus(String id, int accept, int decline) {
        String status = jdbcTemplate.queryForObject("SELECT status FROM Package WHERE package_id = ?", new Object[]{id}, String.class);
        if (status == null)
            return;

        if (status.equalsIgnoreCase("declined") || status.equalsIgnoreCase("delivered"))
            return;

        String date = DatePicker.getDate();

        if (accept == 1){
            jdbcTemplate.update("UPDATE Package SET status = 'delivered' where package_id = ?;", id);
            jdbcTemplate.update("UPDATE Package SET delivery_date = ? where package_id = ?;", date, id);
        }
        else if (decline == 1){
            jdbcTemplate.update("UPDATE Package SET status = 'declined' where package_id = ?;", id);
            jdbcTemplate.update("UPDATE Package SET delivery_date = ? where package_id = ?;", date, id);
        }
    }

    public Package findPackageById(String id) {
        List<Package> query = jdbcTemplate.query("SELECT * FROM Package WHERE package_id = ?", new Object[]{id}, new BeanPropertyRowMapper(Package.class));
        return query.size() > 0 ? query.get(0) : null;
    }
}
